package me.stream.ganglia.tools;

import io.vertx.core.Vertx;
import me.stream.ganglia.core.config.ConfigManager;
import io.vertx.core.Future;
import me.stream.ganglia.core.llm.ModelGateway;
import me.stream.ganglia.core.model.SessionContext;
import me.stream.ganglia.core.prompt.PromptEngine;
import me.stream.ganglia.core.session.SessionManager;
import me.stream.ganglia.skills.SkillManifest;
import me.stream.ganglia.skills.SkillRegistry;
import me.stream.ganglia.skills.SkillTools;
import me.stream.ganglia.tools.model.ToolCall;
import me.stream.ganglia.tools.model.ToolDefinition;
import me.stream.ganglia.tools.model.ToolInvokeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of ToolExecutor that orchestrates built-in tool sets and skill tools.
 */
public class DefaultToolExecutor implements ToolExecutor {
    private static final Logger log = LoggerFactory.getLogger(DefaultToolExecutor.class);

    private final List<ToolSet> builtInToolSets = new ArrayList<>();
    private final SkillRegistry skillRegistry;
    private final Map<String, ToolSet> skillToolCache = new ConcurrentHashMap<>();

    public DefaultToolExecutor(ToolsFactory factory, 
                               SkillRegistry skillRegistry,
                               ModelGateway model,
                               SessionManager sessionManager,
                               PromptEngine promptEngine,
                               ConfigManager config) {
        this.skillRegistry = skillRegistry;

        // Add all built-in toolsets
        builtInToolSets.add(factory.getVertxFileSystemTools());
        builtInToolSets.add(factory.getBashFileSystemTools());
        builtInToolSets.add(factory.getToDoTools());
        builtInToolSets.add(factory.getKnowledgeBaseTools());
        builtInToolSets.add(factory.getInteractionTools());
        builtInToolSets.add(factory.getWebFetchTools());
        builtInToolSets.add(factory.getBashTools());
        builtInToolSets.add(factory.getFileEditTools());
        
        // Add SubAgentTools (passing 'this' as the executor for the child)
        builtInToolSets.add(factory.createSubAgentTools(model, sessionManager, promptEngine, config, this));
        
        builtInToolSets.add(new SkillTools(skillRegistry));
    }

    @Override
    public Future<ToolInvokeResult> execute(ToolCall toolCall, SessionContext context) {
        String toolName = toolCall.toolName();
        log.debug("[TOOL_INVOKE] Name: {}, ID: {}, Args: {}", toolName, toolCall.id(), toolCall.arguments());

        // 1. Try built-in tools
        for (ToolSet ts : builtInToolSets) {
            if (hasTool(ts, toolName)) {
                log.debug("Found tool {} in built-in toolset: {}", toolName, ts.getClass().getSimpleName());
                return ts.execute(toolCall, context)
                    .onSuccess(res -> log.debug("[TOOL_RESULT] Name: {}, ID: {}, Status: {}", toolName, toolCall.id(), res.status()))
                    .onFailure(err -> log.error("[TOOL_ERROR] Name: {}, ID: {}, Error: {}", toolName, toolCall.id(), err.getMessage()));
            }
        }

        // 2. Try tools from active skills
        for (String skillId : context.activeSkillIds()) {
            ToolSet ts = getSkillToolSet(skillId);
            if (ts != null && hasTool(ts, toolName)) {
                log.debug("Found tool {} in active skills : {} (Skill: {})", toolName, ts.getClass().getSimpleName(), skillId);
                return ts.execute(toolCall, context)
                    .onSuccess(res -> log.debug("[SKILL_RESULT] Name: {}, ID: {}, Status: {}", toolName, toolCall.id(), res.status()))
                    .onFailure(err -> log.error("[SKILL_ERROR] Name: {}, ID: {}, Error: {}", toolName, toolCall.id(), err.getMessage()));
            }
        }

        log.warn("No tool implementation found for: {}", toolName);
        return Future.succeededFuture(ToolInvokeResult.error("Unknown tool: " + toolName));
    }

    @Override
    public List<ToolDefinition> getAvailableTools(SessionContext context) {
        List<ToolDefinition> tools = new ArrayList<>();

        // 1. Add built-in tools
        for (ToolSet ts : builtInToolSets) {
            tools.addAll(ts.getDefinitions());
        }

        // 2. Add tools from active skills
        if (context.activeSkillIds() != null) {
            for (String skillId : context.activeSkillIds()) {
                ToolSet ts = getSkillToolSet(skillId);
                if (ts != null) {
                    tools.addAll(ts.getDefinitions());
                }
            }
        }

        return tools;
    }

    private boolean hasTool(ToolSet ts, String toolName) {
        return ts.getDefinitions().stream().anyMatch(d -> d.name().equals(toolName));
    }

    private ToolSet getSkillToolSet(String skillId) {
        if (skillRegistry == null) return null;

        return skillToolCache.computeIfAbsent(skillId, id -> {
            SkillManifest skill = skillRegistry.getSkill(id);
            // TODO 没有Tool的Skill不应该存在吗？，多个Tool的支持

            if (skill == null || skill.tools().isEmpty()) return null;

            // For now, we only support skills that provide ONE ToolSet class.
            // In a more advanced implementation, we'd handle multiple classes or individual tool methods.
            String className = skill.tools().get(0);
            try {
                Class<?> clazz = Class.forName(className);
                // Try to find a constructor that takes SkillRegistry or Vertx if needed,
                // but for now let's assume a default constructor or one we can handle.
                return (ToolSet) clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                log.error("Failed to instantiate tool class {} for skill {}", className, id, e);
                return null;
            }
        });
    }
}

