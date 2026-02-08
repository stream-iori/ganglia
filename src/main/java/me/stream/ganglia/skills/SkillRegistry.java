package me.stream.ganglia.skills;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.file.FileSystem;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SkillRegistry {
    private static final Logger log = LoggerFactory.getLogger(SkillRegistry.class);

    private final Vertx vertx;
    private final Path skillsBaseDir;
    private final Map<String, SkillManifest> skills = new ConcurrentHashMap<>();

    public SkillRegistry(Vertx vertx, Path skillsBaseDir) {
        this.vertx = vertx;
        this.skillsBaseDir = skillsBaseDir;
    }

    public Future<Void> init() {
        FileSystem fs = vertx.fileSystem();
        return fs.exists(skillsBaseDir.toString())
            .compose(exists -> {
                if (!exists) {
                    log.warn("Skills directory does not exist: {}", skillsBaseDir);
                    return Future.succeededFuture();
                }
                return fs.readDir(skillsBaseDir.toString())
                    .compose(list -> {
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
        String manifestPath = skillPath + "/skill.json";
        return fs.exists(manifestPath)
            .compose(exists -> {
                if (!exists) return Future.succeededFuture();
                return fs.readFile(manifestPath)
                    .map(buffer -> {
                        JsonObject json = new JsonObject(buffer);
                        SkillManifest manifest = SkillManifest.fromJson(json);
                        skills.put(manifest.id(), manifest);
                        log.info("Loaded skill: {} ({})", manifest.name(), manifest.id());
                        return null;
                    });
            });
    }

    public List<SkillManifest> listAvailableSkills() {
        return new ArrayList<>(skills.values());
    }

    public SkillManifest getSkill(String id) {
        return skills.get(id);
    }
}
