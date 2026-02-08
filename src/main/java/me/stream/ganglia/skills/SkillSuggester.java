package me.stream.ganglia.skills;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SkillSuggester {
    private static final Logger log = LoggerFactory.getLogger(SkillSuggester.class);

    private final Vertx vertx;
    private final SkillRegistry registry;

    public SkillSuggester(Vertx vertx, SkillRegistry registry) {
        this.vertx = vertx;
        this.registry = registry;
    }

    public Future<String> suggestSkills(String workingDir, List<String> activeSkillIds) {
        if (registry == null) return Future.succeededFuture("");

        return vertx.fileSystem().readDir(workingDir)
            .map(files -> {
                List<String> filenames = files.stream()
                    .map(f -> {
                        int lastSlash = f.lastIndexOf('/');
                        return lastSlash == -1 ? f : f.substring(lastSlash + 1);
                    })
                    .collect(Collectors.toList());

                List<SkillManifest> availableSkills = registry.listAvailableSkills();
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
            // Simple glob-to-regex conversion (very basic)
            String regex = p.replace(".", "\\.").replace("*", ".*");
            Pattern pattern = Pattern.compile("^" + regex + "$");
            for (String file : filenames) {
                if (pattern.matcher(file).matches()) return true;
            }
        }
        return false;
    }
}
