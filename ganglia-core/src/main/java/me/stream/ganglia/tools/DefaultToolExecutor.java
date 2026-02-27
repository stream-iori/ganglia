package me.stream.ganglia.tools;

import me.stream.ganglia.core.config.ConfigManager;
import io.vertx.core.Future;
import me.stream.ganglia.core.llm.ModelGateway;
import me.stream.ganglia.core.model.SessionContext;
import me.stream.ganglia.core.prompt.PromptEngine;
import me.stream.ganglia.core.session.SessionManager;
import me.stream.ganglia.core.llm.util.ToolCallValidator;
import me.stream.ganglia.memory.ContextCompressor;
import me.stream.ganglia.skills.SkillRuntime;
import me.stream.ganglia.skills.SkillService;
import me.stream.ganglia.skills.SkillTools;
import me.stream.ganglia.tools.model.ToolCall;
import me.stream.ganglia.tools.model.ToolDefinition;
import me.stream.ganglia.tools.model.ToolInvokeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of ToolExecutor that orchestrates built-in tool sets and skill tools.
 */
public class DefaultToolExecutor implements ToolExecutor {
    private static final Logger log = LoggerFactory.getLogger(DefaultToolExecutor.class);

    private final List<ToolSet> builtInToolSets = new ArrayList<>();
    private final SkillService skillService;
    private final SkillRuntime skillRuntime;
    private final ToolCallValidator validator = new ToolCallValidator();

    public DefaultToolExecutor(ToolsFactory factory,
                               SkillService skillService,
                               SkillRuntime skillRuntime,
                               ModelGateway model,
                               SessionManager sessionManager,
                               PromptEngine promptEngine,
                               ConfigManager config,
                               ContextCompressor compressor) {
        this.skillService = skillService;
        this.skillRuntime = skillRuntime;

        // Add all built-in toolsets
        builtInToolSets.add(factory.getBashFileSystemTools());
        builtInToolSets.add(factory.getToDoTools());
        builtInToolSets.add(factory.getKnowledgeBaseTools());
        builtInToolSets.add(factory.getInteractionTools());
        builtInToolSets.add(factory.getWebFetchTools());
        builtInToolSets.add(factory.getBashTools());
        builtInToolSets.add(factory.getFileEditTools());

        // Add SubAgentTools (passing 'this' as the executor for the child)
        builtInToolSets.add(factory.createSubAgentTools(model, sessionManager, promptEngine, config, this, compressor));

        builtInToolSets.add(new SkillTools(skillService, skillRuntime));
    }

    @Override
    public Future<ToolInvokeResult> execute(ToolCall toolCall, SessionContext context) {
        String toolName = toolCall.toolName();
        log.debug("[TOOL_INVOKE] Name: {}, ID: {}, Args: {}", toolName, toolCall.id(), toolCall.arguments());

        // 1. Find the tool definition for validation
        ToolDefinition definition = findDefinition(toolCall, context);
        if (definition != null) {
            String validationError = validator.validate(toolName, toolCall.arguments(), definition.jsonSchema());
            if (validationError != null) {
                log.warn("[TOOL_VALIDATION_ERROR] Name: {}, Error: {}", toolName, validationError);
                return Future.succeededFuture(ToolInvokeResult.error(validationError));
            }
        }

        // 2. Try built-in tools
        for (ToolSet ts : builtInToolSets) {
            if (hasTool(ts, toolName)) {
                log.debug("Found tool {} in built-in toolset: {}", toolName, ts.getClass().getSimpleName());
                return ts.execute(toolCall, context)
                    .onSuccess(res -> log.debug("[TOOL_RESULT] Name: {}, ID: {}, Status: {}", toolName, toolCall.id(), res.status()))
                    .onFailure(err -> log.error("[TOOL_ERROR] Name: {}, ID: {}, Error: {}", toolName, toolCall.id(), err.getMessage()));
            }
        }

        // 3. Try tools from active skills
        List<ToolSet> skillTools = skillRuntime.getActiveSkillsTools(context);
        for (ToolSet ts : skillTools) {
            if (hasTool(ts, toolName)) {
                log.debug("Found tool {} in active skills", toolName);
                return ts.execute(toolCall, context)
                    .onSuccess(res -> log.debug("[SKILL_RESULT] Name: {}, ID: {}, Status: {}", toolName, toolCall.id(), res.status()))
                    .onFailure(err -> log.error("[SKILL_ERROR] Name: {}, ID: {}, Error: {}", toolName, toolCall.id(), err.getMessage()));
            }
        }

        log.warn("No tool implementation found for: {}", toolName);
        return Future.succeededFuture(ToolInvokeResult.error("Unknown tool: " + toolName));
    }

    private ToolDefinition findDefinition(ToolCall call, SessionContext context) {
        return getAvailableTools(context).stream()
            .filter(d -> d.name().equals(call.toolName()))
            .findFirst()
            .orElse(null);
    }

    @Override
    public List<ToolDefinition> getAvailableTools(SessionContext context) {
        List<ToolDefinition> tools = new ArrayList<>();

        // 1. Add built-in tools
        for (ToolSet ts : builtInToolSets) {
            tools.addAll(ts.getDefinitions());
        }

        // 2. Add tools from active skills
        List<ToolSet> skillTools = skillRuntime.getActiveSkillsTools(context);
        for (ToolSet ts : skillTools) {
            tools.addAll(ts.getDefinitions());
        }

        return tools;
    }

    private boolean hasTool(ToolSet ts, String toolName) {
        return ts.getDefinitions().stream().anyMatch(d -> d.name().equals(toolName));
    }
}
