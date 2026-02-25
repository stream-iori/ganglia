package me.stream.ganglia.tools;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import me.stream.ganglia.core.config.ConfigManager;
import me.stream.ganglia.core.llm.ModelGateway;
import me.stream.ganglia.core.loop.ReActAgentLoop;
import me.stream.ganglia.core.model.*;
import me.stream.ganglia.core.prompt.PromptEngine;
import me.stream.ganglia.core.session.SessionManager;
import me.stream.ganglia.memory.ContextCompressor;
import me.stream.ganglia.tools.model.ToolCall;
import me.stream.ganglia.tools.model.ToolDefinition;
import me.stream.ganglia.tools.model.ToolInvokeResult;
import me.stream.ganglia.tools.subagent.ContextScoper;
import me.stream.ganglia.tools.subagent.GraphExecutor;
import me.stream.ganglia.tools.subagent.TaskGraph;
import me.stream.ganglia.tools.subagent.TaskNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Tools for spawning specialized sub-agents and orchestrating task graphs.
 */
public class SubAgentTools implements ToolSet {
    private static final Logger logger = LoggerFactory.getLogger(SubAgentTools.class);

    private final Vertx vertx;
    private final ModelGateway modelGateway;
    private final SessionManager sessionManager;
    private final PromptEngine promptEngine;
    private final ConfigManager configManager;
    private final ToolExecutor toolExecutor;
    private final GraphExecutor graphExecutor;
    private final ContextCompressor compressor;

    public SubAgentTools(Vertx vertx, ModelGateway modelGateway, SessionManager sessionManager,
                         PromptEngine promptEngine, ConfigManager configManager, ToolExecutor toolExecutor,
                         GraphExecutor graphExecutor, ContextCompressor compressor) {
        this.vertx = vertx;
        this.modelGateway = modelGateway;
        this.sessionManager = sessionManager;
        this.promptEngine = promptEngine;
        this.configManager = configManager;
        this.toolExecutor = toolExecutor;
        this.graphExecutor = graphExecutor;
        this.compressor = compressor;
    }

    @Override
    public List<ToolDefinition> getDefinitions() {
        return List.of(
            new ToolDefinition(
                "call_sub_agent",
                "Delegate a specific, focused sub-task to a specialized sub-agent. Returns a summary report.",
                """
                {
                  "type": "object",
                  "properties": {
                    "task": { "type": "string", "description": "The task for the sub-agent." },
                    "persona": { "type": "string", "enum": ["INVESTIGATOR", "REFACTORER", "GENERAL"], "default": "GENERAL" }
                  },
                  "required": ["task"]
                }
                """,
                false
            ),
            new ToolDefinition(
                "propose_task_graph",
                "Propose a Directed Acyclic Graph (DAG) of sub-tasks. If 'approved' is false or missing, it will interrupt for user approval. If 'approved' is true, it will execute the graph.",
                """
                {
                  "type": "object",
                  "properties": {
                    "nodes": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "properties": {
                          "id": { "type": "string", "description": "Unique ID for the task node." },
                          "task": { "type": "string", "description": "Description of the sub-task." },
                          "persona": { "type": "string", "enum": ["INVESTIGATOR", "REFACTORER", "GENERAL"], "default": "GENERAL" },
                          "dependencies": {
                            "type": "array",
                            "items": { "type": "string" },
                            "description": "IDs of tasks that must finish before this one starts."
                          }
                        },
                        "required": ["id", "task"]
                      }
                    },
                    "approved": { "type": "boolean", "description": "Set to true ONLY after the user has confirmed the plan.", "default": false }
                  },
                  "required": ["nodes"]
                }
                """,
                true
            )
        );
    }

    @Override
    public Future<ToolInvokeResult> execute(ToolCall call, SessionContext context) {
        return switch (call.toolName()) {
            case "call_sub_agent" -> callSubAgent(call, context);
            case "propose_task_graph" -> proposeTaskGraph(call, context);
            default -> Future.failedFuture("Unknown tool: " + call.toolName());
        };
    }

    @Override
    public Future<ToolInvokeResult> execute(String toolName, Map<String, Object> args, SessionContext context) {
        return execute(new ToolCall(UUID.randomUUID().toString(), toolName, args), context);
    }

    private Future<ToolInvokeResult> callSubAgent(ToolCall call, SessionContext parentContext) {
        String task = (String) call.arguments().get("task");
        String persona = (String) call.arguments().getOrDefault("persona", "GENERAL");

        // 1. Recursion Control
        Object levelObj = parentContext.metadata().getOrDefault("sub_agent_level", 0);
        int currentLevel = (levelObj instanceof Number) ? ((Number) levelObj).intValue() : Integer.parseInt(levelObj.toString());
        
        if (currentLevel >= 1) {
            return Future.succeededFuture(ToolInvokeResult.error("RECURSION_LIMIT: Nested sub-agents are not allowed."));
        }

        // 2. Prepare Child Context metadata
        String childSessionId = parentContext.sessionId() + "-sub-" + UUID.randomUUID().toString().substring(0, 4);
        Map<String, Object> childMetadata = new HashMap<>();
        childMetadata.put("sub_agent_level", currentLevel + 1);
        childMetadata.put("is_sub_agent", true);
        childMetadata.put("sub_agent_persona", persona);

        SessionContext childContext = ContextScoper.scope(childSessionId, parentContext, childMetadata);

        ReActAgentLoop childLoop = new ReActAgentLoop(vertx, modelGateway, toolExecutor, sessionManager, promptEngine, configManager, compressor);

        return childLoop.run("TASK: " + task, childContext)
            .map(report -> ToolInvokeResult.success("--- SUB-AGENT REPORT ---\n" + report + "\n--- END REPORT ---"))
            .recover(err -> Future.succeededFuture(ToolInvokeResult.error("SUB_AGENT_ERROR: " + err.getMessage())));
    }

    private Future<ToolInvokeResult> proposeTaskGraph(ToolCall call, SessionContext context) {
        // Check recursion
        Object levelObj = context.metadata().getOrDefault("sub_agent_level", 0);
        int currentLevel = (levelObj instanceof Number) ? ((Number) levelObj).intValue() : Integer.parseInt(levelObj.toString());
        if (currentLevel >= 1) {
            return Future.succeededFuture(ToolInvokeResult.error("RECURSION_LIMIT: Nested task graphs are not allowed."));
        }

        Object approvedObj = call.arguments().getOrDefault("approved", false);
        boolean approved = (approvedObj instanceof Boolean) ? (Boolean) approvedObj : Boolean.parseBoolean(approvedObj.toString());

        if (!approved) {
            return interruptForApproval(call);
        }

        // If it was approved, execute
        return executeGraph(call, context);
    }

    private Future<ToolInvokeResult> interruptForApproval(ToolCall call) {
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) call.arguments().get("nodes");
        
        StringBuilder sb = new StringBuilder("PROPOSED TASK GRAPH:\n\n");
        for (Map<String, Object> node : nodes) {
            sb.append("- [").append(node.get("id")).append("] ").append(node.get("task"));
            List<String> deps = (List<String>) node.get("dependencies");
            if (deps != null && !deps.isEmpty()) {
                sb.append(" (Depends on: ").append(String.join(", ", deps)).append(")");
            }
            sb.append("\n");
        }
        sb.append("\nDo you approve this execution plan? (y/n)");

        return Future.succeededFuture(ToolInvokeResult.interrupt(sb.toString()));
    }

    private Future<ToolInvokeResult> executeGraph(ToolCall call, SessionContext context) {
        List<Map<String, Object>> nodeData = (List<Map<String, Object>>) call.arguments().get("nodes");
        List<TaskNode> nodes = new ArrayList<>();
        
        for (Map<String, Object> data : nodeData) {
            nodes.add(new TaskNode(
                (String) data.get("id"),
                (String) data.get("task"),
                (String) data.getOrDefault("persona", "GENERAL"),
                (List<String>) data.getOrDefault("dependencies", Collections.emptyList()),
                null
            ));
        }

        TaskGraph graph = new TaskGraph(nodes);
        return graphExecutor.execute(graph, context)
            .map(ToolInvokeResult::success)
            .recover(err -> Future.succeededFuture(ToolInvokeResult.error("GRAPH_EXECUTION_ERROR: " + err.getMessage())));
    }
}
