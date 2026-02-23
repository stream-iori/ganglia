package me.stream.ganglia.tools;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import me.stream.ganglia.core.config.ConfigManager;
import me.stream.ganglia.core.llm.ModelGateway;
import me.stream.ganglia.core.loop.ReActAgentLoop;
import me.stream.ganglia.core.model.*;
import me.stream.ganglia.core.prompt.PromptEngine;
import me.stream.ganglia.core.session.SessionManager;
import me.stream.ganglia.tools.model.ToolCall;
import me.stream.ganglia.tools.model.ToolDefinition;
import me.stream.ganglia.tools.model.ToolInvokeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tools for spawning specialized sub-agents to handle focused tasks.
 */
public class SubAgentTools implements ToolSet {
    private static final Logger logger = LoggerFactory.getLogger(SubAgentTools.class);

    private final Vertx vertx;
    private final ModelGateway modelGateway;
    private final SessionManager sessionManager;
    private final PromptEngine promptEngine;
    private final ConfigManager configManager;
    private final ToolExecutor toolExecutor;

    public SubAgentTools(Vertx vertx, ModelGateway modelGateway, SessionManager sessionManager,
                         PromptEngine promptEngine, ConfigManager configManager, ToolExecutor toolExecutor) {
        this.vertx = vertx;
        this.modelGateway = modelGateway;
        this.sessionManager = sessionManager;
        this.promptEngine = promptEngine;
        this.configManager = configManager;
        this.toolExecutor = toolExecutor;
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
            )
        );
    }

    @Override
    public Future<ToolInvokeResult> execute(ToolCall call, SessionContext context) {
        if ("call_sub_agent".equals(call.toolName())) {
            return callSubAgent(call, context);
        }
        return Future.failedFuture("Unknown tool: " + call.toolName());
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

        // 2. Prepare Child Context
        String childSessionId = parentContext.sessionId() + "-sub-" + UUID.randomUUID().toString().substring(0, 4);
        Map<String, Object> childMetadata = new java.util.HashMap<>(parentContext.metadata());
        childMetadata.put("sub_agent_level", currentLevel + 1);
        childMetadata.put("is_sub_agent", true);
        childMetadata.put("sub_agent_persona", persona);

        SessionContext childContext = new SessionContext(
            childSessionId,
            Collections.emptyList(),
            null,
            childMetadata,
            parentContext.activeSkillIds(),
            parentContext.modelOptions(),
            parentContext.toDoList()
        );

        ReActAgentLoop childLoop = new ReActAgentLoop(vertx, modelGateway, toolExecutor, sessionManager, promptEngine, configManager);

        return childLoop.run("TASK: " + task, childContext)
            .map(report -> ToolInvokeResult.success("--- SUB-AGENT REPORT ---\\n" + report + "\\n--- END REPORT ---"))
            .recover(err -> Future.succeededFuture(ToolInvokeResult.error("SUB_AGENT_ERROR: " + err.getMessage())));
    }
}
