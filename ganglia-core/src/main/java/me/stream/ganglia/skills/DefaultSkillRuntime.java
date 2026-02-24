package me.stream.ganglia.skills;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import me.stream.ganglia.core.model.SessionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class DefaultSkillRuntime implements SkillRuntime {
    private static final Logger log = LoggerFactory.getLogger(DefaultSkillRuntime.class);

    private final Vertx vertx;
    private final SkillService skillService;
    private final Map<String, ClassLoader> classLoaderCache = new ConcurrentHashMap<>();

    public DefaultSkillRuntime(Vertx vertx, SkillService skillService) {
        this.vertx = vertx;
        this.skillService = skillService;
    }

    @Override
    public Future<SessionContext> activateSkill(String skillId, SessionContext context) {
        Optional<SkillManifest> skill = skillService.getSkill(skillId);
        if (skill.isEmpty()) {
            return Future.failedFuture("Skill not found: " + skillId);
        }

        List<String> activeSkills = new ArrayList<>(context.activeSkillIds());
        if (activeSkills.contains(skillId)) {
            return Future.succeededFuture(context);
        }

        activeSkills.add(skillId);
        return Future.succeededFuture(new SessionContext(
            context.sessionId(),
            context.previousTurns(),
            context.currentTurn(),
            context.metadata(),
            activeSkills,
            context.modelOptions(),
            context.toDoList()
        ));
    }

    @Override
    public Future<SessionContext> deactivateSkill(String skillId, SessionContext context) {
        List<String> activeSkills = new ArrayList<>(context.activeSkillIds());
        if (!activeSkills.contains(skillId)) {
            return Future.succeededFuture(context);
        }

        activeSkills.remove(skillId);
        return Future.succeededFuture(new SessionContext(
            context.sessionId(),
            context.previousTurns(),
            context.currentTurn(),
            context.metadata(),
            activeSkills,
            context.modelOptions(),
            context.toDoList()
        ));
    }

    @Override
    public Future<String> getActiveSkillsPrompt(SessionContext context) {
        List<String> activeSkillIds = context.activeSkillIds();
        if (activeSkillIds == null || activeSkillIds.isEmpty()) {
            return Future.succeededFuture("");
        }

        List<Future<String>> futures = new ArrayList<>();
        for (String skillId : activeSkillIds) {
            skillService.getSkill(skillId).ifPresent(skill -> futures.add(loadSkillPrompts(skill)));
        }

        if (futures.isEmpty()) return Future.succeededFuture("");

        return Future.join(futures).map(composite -> {
            StringBuilder sb = new StringBuilder();
            sb.append("\n# ACTIVE SKILLS\n");
            sb.append("The following specialized skills are currently active and provide domain-specific instructions.\n");
            for (int i = 0; i < futures.size(); i++) {
                sb.append(composite.resultAt(i).toString());
            }
            return sb.toString();
        });
    }

    private Future<String> loadSkillPrompts(SkillManifest skill) {
        if (skill.instructions() != null && !skill.instructions().isEmpty()) {
            return Future.succeededFuture(
                "\n## Skill: " + skill.name() + " (" + skill.id() + ")\n" +
                skill.instructions() + "\n"
            );
        }
        return Future.succeededFuture("\n## Skill: " + skill.name() + " (No detailed instructions available)\n");
    }

    @Override
    public List<me.stream.ganglia.tools.ToolSet> getActiveSkillsTools(SessionContext context) {
        List<String> activeSkillIds = context.activeSkillIds();
        if (activeSkillIds == null || activeSkillIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<me.stream.ganglia.tools.ToolSet> toolSets = new ArrayList<>();
        for (String skillId : activeSkillIds) {
            skillService.getSkill(skillId).ifPresent(skill -> {
                List<SkillToolDefinition> scriptTools = skill.skillTools().stream()
                    .filter(t -> "SCRIPT".equals(t.type()))
                    .toList();
                
                List<SkillToolDefinition> javaTools = skill.skillTools().stream()
                    .filter(t -> "JAVA".equals(t.type()))
                    .toList();

                // 1. Handle Script Tools
                if (!scriptTools.isEmpty()) {
                    toolSets.add(new ScriptSkillToolSet(vertx, skill.id(), skill.skillDir(), scriptTools));
                }

                // 2. Handle Java Tools
                if (!javaTools.isEmpty()) {
                    ClassLoader cl = getClassLoader(skill);
                    toolSets.add(new JavaSkillToolSet(skill.id(), cl, javaTools));
                }

                // 3. Handle Legacy Java Tools (className平铺在tools里)
                if (skill.tools() != null && !skill.tools().isEmpty()) {
                    for (String className : skill.tools()) {
                        try {
                            Class<?> clazz = Class.forName(className);
                            toolSets.add((me.stream.ganglia.tools.ToolSet) clazz.getDeclaredConstructor().newInstance());
                        } catch (Exception e) {
                            log.error("Failed to instantiate legacy tool class {} for skill {}", className, skillId, e);
                        }
                    }
                }
            });
        }
        return toolSets;
    }

    private ClassLoader getClassLoader(SkillManifest skill) {
        if (skill.jarPath() == null) {
            return getClass().getClassLoader();
        }
        return classLoaderCache.computeIfAbsent(skill.id(), id -> {
            try {
                java.net.URL jarUrl = new java.io.File(skill.jarPath()).toURI().toURL();
                return new java.net.URLClassLoader(new java.net.URL[]{jarUrl}, getClass().getClassLoader());
            } catch (Exception e) {
                log.error("Failed to create ClassLoader for skill JAR {}: {}", skill.jarPath(), e.getMessage());
                return getClass().getClassLoader();
            }
        });
    }

    @Override
    public Future<String> suggestSkills(SessionContext context) {
        // Simple suggestion based on current working directory
        String workingDir = System.getProperty("user.dir");
        List<String> activeSkillIds = context.activeSkillIds();

        return vertx.fileSystem().readDir(workingDir)
            .map(files -> {
                List<String> filenames = files.stream()
                    .map(f -> {
                        int lastSlash = f.lastIndexOf('/');
                        return lastSlash == -1 ? f : f.substring(lastSlash + 1);
                    })
                    .collect(Collectors.toList());

                List<SkillManifest> availableSkills = skillService.getAvailableSkills();
                List<SkillManifest> suggestions = new ArrayList<>();

                for (SkillManifest skill : availableSkills) {
                    if (activeSkillIds != null && activeSkillIds.contains(skill.id())) continue;

                    if (matchTriggers(skill, filenames)) {
                        suggestions.add(skill);
                    }
                }

                if (suggestions.isEmpty()) return "";

                StringBuilder sb = new StringBuilder("\n## Skill Suggestions\n");
                sb.append("Based on the files in the current directory, you might find these skills useful:\n");
                for (SkillManifest s : suggestions) {
                    sb.append("- ").append(s.id()).append(": ").append(s.name()).append(" (use activate_skill to enable)\n");
                }
                return sb.toString();
            })
            .recover(err -> {
                log.warn("Failed to read dir for skill suggestions: {}", workingDir, err);
                return Future.succeededFuture("");
            });
    }

    private boolean matchTriggers(SkillManifest skill, List<String> filenames) {
        if (skill.activationTriggers() == null) return false;

        List<String> patterns = skill.activationTriggers().filePatterns();
        if (patterns == null || patterns.isEmpty()) return false;

        for (String p : patterns) {
            String regex = p.replace(".", "\\.").replace("*", ".*");
            Pattern pattern = Pattern.compile("^" + regex + "$");
            for (String file : filenames) {
                if (pattern.matcher(file).matches()) return true;
            }
        }
        return false;
    }
}
