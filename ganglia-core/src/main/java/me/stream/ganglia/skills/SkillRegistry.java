package me.stream.ganglia.skills;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.file.FileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SkillRegistry {
    private static final Logger log = LoggerFactory.getLogger(SkillRegistry.class);

    private final Vertx vertx;
    private final List<Path> skillsBaseDirs;
    private final Map<String, SkillManifest> skills = new ConcurrentHashMap<>();

    public SkillRegistry(Vertx vertx, List<Path> skillsBaseDirs) {
        this.vertx = vertx;
        this.skillsBaseDirs = skillsBaseDirs;
    }

    public Future<Void> init() {
        List<Future<Void>> futures = new ArrayList<>();
        for (Path dir : skillsBaseDirs) {
            futures.add(scanDirectory(dir));
        }
        return Future.join(futures).mapEmpty();
    }

    private Future<Void> scanDirectory(Path baseDir) {
        FileSystem fs = vertx.fileSystem();
        log.debug("Scanning for skills in directory: {}", baseDir);
        return fs.exists(baseDir.toString())
            .compose(exists -> {
                if (!exists) {
                    log.debug("Skills directory does not exist: {}", baseDir);
                    return Future.succeededFuture();
                }
                return fs.readDir(baseDir.toString())
                    .compose(list -> {
                        log.debug("Found {} potential skill folders in {}", list.size(), baseDir);
                        List<Future<Void>> futures = new ArrayList<>();
                        for (String skillPath : list) {
                            futures.add(loadSkill(skillPath));
                        }
                        return Future.join(futures).mapEmpty();
                    });
            });
    }

    private Future<Void> loadSkill(String skillPath) {
        FileSystem fs = vertx.fileSystem();
        String skillMdPath = skillPath + "/SKILL.md";
        String skillJsonPath = skillPath + "/skill.json";

        return fs.exists(skillMdPath)
            .compose(mdExists -> {
                if (mdExists) {
                    return fs.readFile(skillMdPath)
                        .map(buffer -> {
                            String folderName = Paths.get(skillPath).getFileName().toString();
                            SkillManifest manifest = SkillManifest.fromMarkdown(folderName, buffer.toString());
                            skills.put(manifest.id(), manifest);
                            log.info("Loaded skill (MD): {} ({})", manifest.name(), manifest.id());
                            return null;
                        });
                } else {
                    return fs.exists(skillJsonPath)
                        .compose(jsonExists -> {
                            if (!jsonExists) return Future.succeededFuture();
                            return fs.readFile(skillJsonPath)
                                .map(buffer -> {
                                    SkillManifest manifest = SkillManifest.fromJson(buffer.toJsonObject());
                                    skills.put(manifest.id(), manifest);
                                    log.info("Loaded skill (JSON): {} ({})", manifest.name(), manifest.id());
                                    return null;
                                });
                        });
                }
            });
    }

    public List<SkillManifest> listAvailableSkills() {
        return new ArrayList<>(skills.values());
    }

    public SkillManifest getSkill(String id) {
        return skills.get(id);
    }
}
