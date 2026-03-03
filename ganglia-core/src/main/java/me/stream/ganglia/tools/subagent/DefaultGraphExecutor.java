package me.stream.ganglia.tools.subagent;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import me.stream.ganglia.core.config.ConfigManager;
import me.stream.ganglia.core.llm.ModelGateway;
import me.stream.ganglia.core.loop.ReActAgentLoop;
import me.stream.ganglia.core.model.SessionContext;
import me.stream.ganglia.core.prompt.PromptEngine;
import me.stream.ganglia.core.session.SessionManager;
import me.stream.ganglia.core.schedule.ScheduleableFactory;
import me.stream.ganglia.memory.ContextCompressor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class DefaultGraphExecutor implements GraphExecutor {
    private static final Logger logger = LoggerFactory.getLogger(DefaultGraphExecutor.class);

    private final Vertx vertx;
    private final ModelGateway modelGateway;
    private final SessionManager sessionManager;
    private final PromptEngine promptEngine;
    private final ConfigManager configManager;
    private final ContextCompressor compressor;
    private ScheduleableFactory scheduleableFactory;

    public DefaultGraphExecutor(Vertx vertx, ModelGateway modelGateway, SessionManager sessionManager, 
                                PromptEngine promptEngine, ConfigManager configManager, ContextCompressor compressor) {
        this.vertx = vertx;
        this.modelGateway = modelGateway;
        this.sessionManager = sessionManager;
        this.promptEngine = promptEngine;
        this.configManager = configManager;
        this.compressor = compressor;
    }

    public void setScheduleableFactory(ScheduleableFactory scheduleableFactory) {
        this.scheduleableFactory = scheduleableFactory;
    }

    @Override
    public Future<String> execute(TaskGraph graph, SessionContext parentContext) {
        logger.info("Executing TaskGraph with {} nodes.", graph.nodes().size());
        
        if (scheduleableFactory == null) {
            return Future.failedFuture("ScheduleableFactory not configured for GraphExecutor");
        }
        
        Map<String, Future<String>> results = new ConcurrentHashMap<>();
        Map<String, TaskNode> nodeMap = graph.nodes().stream()
            .collect(Collectors.toMap(TaskNode::id, node -> node));

        // 1. Kick off all nodes (executeNodeRecursive will handle dependencies)
        List<Future<String>> allFutures = graph.nodes().stream()
            .map(node -> executeNodeRecursive(node, nodeMap, results, parentContext))
            .toList();

        return Future.all(allFutures)
            .map(cf -> {
                StringBuilder sb = new StringBuilder("--- TASK GRAPH EXECUTION REPORT ---\\n\\n");
                for (TaskNode node : graph.nodes()) {
                    sb.append("NODE ID: ").append(node.id()).append("\\n");
                    sb.append("TASK: ").append(node.task()).append("\\n");
                    sb.append("STATUS: SUCCESS\\n");
                    sb.append("REPORT:\\n").append(results.get(node.id()).result()).append("\\n\\n");
                    sb.append("-----------------------------------\\n");
                }
                sb.append("--- END OF GRAPH REPORT ---");
                return sb.toString();
            })
            .recover(err -> {
                logger.error("Graph execution failed: {}", err.getMessage());
                return Future.failedFuture(err);
            });
    }

    private Future<String> executeNodeRecursive(TaskNode node, Map<String, TaskNode> nodeMap,
                                                Map<String, Future<String>> results, SessionContext parentContext) {
        return results.computeIfAbsent(node.id(), id -> {
            List<String> deps = node.dependencies();
            if (deps == null || deps.isEmpty()) {
                return runSubAgent(node, parentContext, Collections.emptyMap());
            }

            List<Future<String>> depFutures = deps.stream()
                .map(depId -> executeNodeRecursive(nodeMap.get(depId), nodeMap, results, parentContext))
                .toList();

            return Future.all(depFutures)
                .compose(cf -> {
                    Map<String, String> depResults = new HashMap<>();
                    for (int i = 0; i < deps.size(); i++) {
                        // resultAt(i) returns the result of the i-th future in depFutures
                        depResults.put(deps.get(i), (String) cf.resultAt(i));
                    }
                    return runSubAgent(node, parentContext, depResults);
                });
        });
    }

    private Future<String> runSubAgent(TaskNode node, SessionContext parentContext, Map<String, String> dependencyResults) {
        logger.info("Starting Sub-Agent for node: {} (Persona: {})", node.id(), node.persona());

        // Prepare context metadata
        String childSessionId = parentContext.sessionId() + "-sub-" + node.id() + "-" + UUID.randomUUID().toString().substring(0, 4);
        Map<String, Object> childMetadata = new HashMap<>();
        childMetadata.put("is_sub_agent", true);
        childMetadata.put("sub_agent_persona", node.persona());
        childMetadata.put("node_id", node.id());

        // Recursive depth control
        Object levelObj = parentContext.metadata().getOrDefault("sub_agent_level", 0);
        int currentLevel = (levelObj instanceof Number) ? ((Number) levelObj).intValue() : Integer.parseInt(levelObj.toString());
        childMetadata.put("sub_agent_level", currentLevel + 1);

        SessionContext childContext = ContextScoper.scope(childSessionId, parentContext, childMetadata);

        ReActAgentLoop childLoop = new ReActAgentLoop(vertx, modelGateway, scheduleableFactory, sessionManager, promptEngine, configManager, compressor);

        StringBuilder promptBuilder = new StringBuilder("TASK: ").append(node.task()).append("\\n\\n");
        if (!dependencyResults.isEmpty()) {
            promptBuilder.append("PREVIOUS TASK RESULTS:\\n");
            dependencyResults.forEach((depId, report) -> {
                promptBuilder.append("ID: ").append(depId).append("\\n");
                promptBuilder.append("RESULT:\\n").append(report).append("\\n");
            });
        }

        return childLoop.run(promptBuilder.toString(), childContext);
    }
}
