package me.stream.ganglia.skills;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.file.FileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SkillPromptInjector {
    private static final Logger log = LoggerFactory.getLogger(SkillPromptInjector.class);

    private final Vertx vertx;
    private final SkillRegistry registry;

    public SkillPromptInjector(Vertx vertx, SkillRegistry registry) {
        this.vertx = vertx;
        this.registry = registry;
    }

    public Future<String> injectSkills(List<String> activeSkillIds) {
        if (activeSkillIds == null || activeSkillIds.isEmpty()) {
            return Future.succeededFuture("");
        }

        List<Future<String>> futures = new ArrayList<>();
        for (String skillId : activeSkillIds) {
            SkillManifest skill = registry.getSkill(skillId);
            if (skill != null) {
                futures.add(loadSkillPrompts(skill));
            }
        }

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

        // Fallback for legacy skill.json format
        // Note: This might fail if the prompt paths are relative and we don't know the base dir anymore
        // But since SkillRegistry loaded it, we could potentially store the base path in SkillManifest
        
        List<SkillManifest.PromptDefinition> prompts = skill.prompts();
        if (prompts == null || prompts.isEmpty()) {
            return Future.succeededFuture("");
        }

        // Sort by priority (higher first)
        List<SkillManifest.PromptDefinition> sortedPrompts = new ArrayList<>(prompts);
        sortedPrompts.sort((a, b) -> Integer.compare(b.priority(), a.priority()));

        // Legacy support is limited here because we don't have the original base path easily.
        // For now, we'll focus on SKILL.md.
        return Future.succeededFuture("\n## Skill: " + skill.name() + " (Legacy JSON format - prompts not fully loaded)\n");
    }
}
