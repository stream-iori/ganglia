package me.stream.ganglia.core.tools;

import io.vertx.core.Future;
import me.stream.ganglia.core.model.SessionContext;
import me.stream.ganglia.core.skills.SkillManifest;
import me.stream.ganglia.core.skills.SkillRegistry;
import me.stream.ganglia.core.tools.model.ToolCall;
import me.stream.ganglia.core.tools.model.ToolDefinition;
import me.stream.ganglia.core.tools.model.ToolInvokeResult;
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

    public DefaultToolExecutor(ToolsFactory factory, SkillRegistry skillRegistry) {
        this.skillRegistry = skillRegistry;
        
        // Add all built-in toolsets
        builtInToolSets.add(factory.getVertxFileSystemTools());
        builtInToolSets.add(factory.getBashFileSystemTools());
        builtInToolSets.add(factory.getToDoTools());
        builtInToolSets.add(factory.getKnowledgeBaseTools());
        builtInToolSets.add(factory.getSelectionTools());
        builtInToolSets.add(factory.getWebFetchTools());
        builtInToolSets.add(factory.getBashTools());
        builtInToolSets.add(new me.stream.ganglia.core.skills.SkillTools(skillRegistry));
    }

    @Override
    public Future<ToolInvokeResult> execute(ToolCall toolCall, SessionContext context) {
        String toolName = toolCall.toolName();
        
        // 1. Try built-in tools
        for (ToolSet ts : builtInToolSets) {
            if (hasTool(ts, toolName)) {
                return ts.execute(toolName, toolCall.arguments(), context);
            }
        }

        // 2. Try tools from active skills
        if (context.activeSkillIds() != null) {
            for (String skillId : context.activeSkillIds()) {
                ToolSet ts = getSkillToolSet(skillId);
                if (ts != null && hasTool(ts, toolName)) {
                    return ts.execute(toolName, toolCall.arguments(), context);
                }
            }
        }
        
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
            if (skill == null || skill.tools() == null || skill.tools().isEmpty()) return null;
            
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
            
