package me.stream.ganglia.core.schedule.task;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import me.stream.ganglia.core.config.ConfigManager;
import me.stream.ganglia.core.llm.ModelGateway;
import me.stream.ganglia.core.loop.ReActAgentLoop;
import me.stream.ganglia.core.model.SessionContext;
import me.stream.ganglia.core.prompt.PromptEngine;
import me.stream.ganglia.core.schedule.ScheduleResult;
import me.stream.ganglia.core.schedule.Scheduleable;
import me.stream.ganglia.core.schedule.ScheduleableFactory;
import me.stream.ganglia.core.session.SessionManager;
import me.stream.ganglia.memory.ContextCompressor;
import me.stream.ganglia.tools.model.ToolCall;
import me.stream.ganglia.tools.subagent.ContextScoper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SubAgentTask implements Scheduleable {
    private final ToolCall call;
    private final Vertx vertx;
    private final ModelGateway modelGateway;
    private final SessionManager sessionManager;
    private final PromptEngine promptEngine;
    private final ConfigManager configManager;
    private final ContextCompressor compressor;
    private final ScheduleableFactory scheduleableFactory;

    public SubAgentTask(ToolCall call, Vertx vertx, ModelGateway modelGateway, SessionManager sessionManager,
                        PromptEngine promptEngine, ConfigManager configManager, ContextCompressor compressor,
                        ScheduleableFactory scheduleableFactory) {
        this.call = call;
        this.vertx = vertx;
        this.modelGateway = modelGateway;
        this.sessionManager = sessionManager;
        this.promptEngine = promptEngine;
        this.configManager = configManager;
        this.compressor = compressor;
        this.scheduleableFactory = scheduleableFactory;
    }

    @Override
    public String id() {
        return call.id();
    }

    @Override
    public String name() {
        return call.toolName();
    }

    @Override
    public Future<ScheduleResult> execute(SessionContext parentContext) {
        String task = (String) call.arguments().get("task");
        String persona = (String) call.arguments().getOrDefault("persona", "GENERAL");

        // 1. Recursion Control
        Object levelObj = parentContext.metadata().getOrDefault("sub_agent_level", 0);
        int currentLevel = (levelObj instanceof Number) ? ((Number) levelObj).intValue() : Integer.parseInt(levelObj.toString());
        
        if (currentLevel >= 1) {
            return Future.succeededFuture(ScheduleResult.error("RECURSION_LIMIT: Nested sub-agents are not allowed."));
        }

        // 2. Prepare Child Context metadata
        String childSessionId = parentContext.sessionId() + "-sub-" + UUID.randomUUID().toString().substring(0, 4);
        Map<String, Object> childMetadata = new HashMap<>();
        childMetadata.put("sub_agent_level", currentLevel + 1);
        childMetadata.put("is_sub_agent", true);
        childMetadata.put("sub_agent_persona", persona);

        SessionContext childContext = ContextScoper.scope(childSessionId, parentContext, childMetadata);

        // Note: ReActAgentLoop now requires ScheduleableFactory instead of ToolExecutor
        ReActAgentLoop childLoop = new ReActAgentLoop(vertx, modelGateway, scheduleableFactory, sessionManager, promptEngine, configManager, compressor);

        return childLoop.run("TASK: " + task, childContext)
            .map(report -> ScheduleResult.success("--- SUB-AGENT REPORT ---\n" + report + "\n--- END REPORT ---"))
            .recover(err -> Future.succeededFuture(ScheduleResult.error("SUB_AGENT_ERROR: " + err.getMessage())));
    }
}
