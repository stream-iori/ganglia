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
    private final Path skillsBaseDir;

    public SkillPromptInjector(Vertx vertx, SkillRegistry registry, Path skillsBaseDir) {
        this.vertx = vertx;
        this.registry = registry;
        this.skillsBaseDir = skillsBaseDir;
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
            sb.append("## Active Skills\n");
            for (int i = 0; i < futures.size(); i++) {
                sb.append(composite.resultAt(i).toString());
            }
            return sb.toString();
        });
    }

    private Future<String> loadSkillPrompts(SkillManifest skill) {
        FileSystem fs = vertx.fileSystem();
        List<SkillManifest.PromptDefinition> prompts = skill.prompts();
        if (prompts == null || prompts.isEmpty()) {
            return Future.succeededFuture("");
        }

        // Sort by priority (higher first)
        List<SkillManifest.PromptDefinition> sortedPrompts = new ArrayList<>(prompts);
        sortedPrompts.sort((a, b) -> Integer.compare(b.priority(), a.priority()));

        List<Future<String>> futures = new ArrayList<>();
        for (SkillManifest.PromptDefinition def : sortedPrompts) {
            String fullPath = skillsBaseDir.resolve(skill.id()).resolve(def.path()).toString();
            futures.add(fs.readFile(fullPath).map(buffer ->
                "\n### " + skill.name() + " - " + def.id() + "\n" + buffer.toString() + "\n"
            ).recover(err -> {
                log.error("Failed to load prompt {} for skill {}", def.path(), skill.id(), err);
                return Future.succeededFuture("");
            }));
        }

        return Future.join(futures).map(composite -> {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < futures.size(); i++) {
                sb.append(composite.resultAt(i).toString());
            }
            return sb.toString();
        });
    }
}
