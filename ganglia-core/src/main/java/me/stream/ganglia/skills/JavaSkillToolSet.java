package me.stream.ganglia.skills;

import io.vertx.core.Future;
import me.stream.ganglia.core.model.SessionContext;
import me.stream.ganglia.tools.ToolSet;
import me.stream.ganglia.tools.model.ToolCall;
import me.stream.ganglia.tools.model.ToolDefinition;
import me.stream.ganglia.tools.model.ToolInvokeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A ToolSet that delegates to a Java class loaded dynamically from a skill's JAR.
 */
public class JavaSkillToolSet implements ToolSet {
    private static final Logger logger = LoggerFactory.getLogger(JavaSkillToolSet.class);

    private final String skillId;
    private final ClassLoader classLoader;
    private final List<SkillToolDefinition> javaTools;
    private final Map<String, ToolSet> instanceCache = new ConcurrentHashMap<>();

    public JavaSkillToolSet(String skillId, ClassLoader classLoader, List<SkillToolDefinition> javaTools) {
        this.skillId = skillId;
        this.classLoader = classLoader;
        this.javaTools = javaTools;
    }

    @Override
    public List<ToolDefinition> getDefinitions() {
        List<ToolDefinition> allDefs = new ArrayList<>();
        for (SkillToolDefinition def : javaTools) {
            ToolSet instance = getOrInstantiate(def);
            if (instance != null) {
                instance.getDefinitions().stream()
                    .filter(d -> d.name().equals(def.name()))
                    .findFirst()
                    .ifPresent(allDefs::add);
            }
        }
        return allDefs;
    }

    @Override
    public Future<ToolInvokeResult> execute(ToolCall call, SessionContext context) {
        return javaTools.stream()
            .filter(t -> t.name().equals(call.toolName()))
            .findFirst()
            .map(def -> {
                ToolSet instance = getOrInstantiate(def);
                if (instance == null) {
                    return Future.<ToolInvokeResult>failedFuture("Failed to instantiate java tool class: " + def.java().className());
                }
                return instance.execute(call, context);
            })
            .orElseGet(() -> Future.failedFuture("Tool not found in Java skill: " + call.toolName()));
    }

    @Override
    public Future<ToolInvokeResult> execute(String toolName, Map<String, Object> args, SessionContext context) {
        return execute(new ToolCall(java.util.UUID.randomUUID().toString(), toolName, args), context);
    }

    private ToolSet getOrInstantiate(SkillToolDefinition def) {
        if (def.java() == null) return null;
        return instanceCache.computeIfAbsent(def.java().className(), className -> {
            try {
                Class<?> clazz = Class.forName(className, true, classLoader);
                return (ToolSet) clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                logger.error("Failed to instantiate Java tool {} for skill {}: {}", className, skillId, e.getMessage());
                return null;
            }
        });
    }
}
