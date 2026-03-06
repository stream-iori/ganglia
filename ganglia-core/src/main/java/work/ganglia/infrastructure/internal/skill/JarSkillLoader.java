package work.ganglia.infrastructure.internal.skill;

import work.ganglia.port.internal.skill.SkillLoader;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.file.FileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.ganglia.port.internal.skill.SkillManifest;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Loads skills from JAR files.
 */
public class JarSkillLoader implements SkillLoader {
    private static final Logger log = LoggerFactory.getLogger(JarSkillLoader.class);

    private final Vertx vertx;
    private final List<Path> baseDirs;

    public JarSkillLoader(Vertx vertx, List<Path> baseDirs) {
        this.vertx = vertx;
        this.baseDirs = baseDirs;
    }

    @Override
    public Future<List<SkillManifest>> load() {
        List<Future<List<SkillManifest>>> futures = new ArrayList<>();
        for (Path dir : baseDirs) {
            futures.add(scanDirectory(dir));
        }
        return Future.join(futures).map(composite -> {
            List<SkillManifest> all = new ArrayList<>();
            for (int i = 0; i < futures.size(); i++) {
                all.addAll(composite.resultAt(i));
            }
            return all;
        });
    }

    private Future<List<SkillManifest>> scanDirectory(Path baseDir) {
        FileSystem fs = vertx.fileSystem();
        return fs.exists(baseDir.toString())
            .compose(exists -> {
                if (!exists) return Future.succeededFuture(List.of());
                return fs.readDir(baseDir.toString())
                    .compose(list -> {
                        List<Future<SkillManifest>> futures = new ArrayList<>();
                        for (String path : list) {
                            if (path.endsWith(".jar")) {
                                futures.add(loadFromJar(path));
                            }
                        }
                        return Future.join(futures).map(composite -> {
                            List<SkillManifest> loaded = new ArrayList<>();
                            for (int i = 0; i < futures.size(); i++) {
                                SkillManifest m = composite.resultAt(i);
                                if (m != null) loaded.add(m);
                            }
                            return loaded;
                        });
                    });
            });
    }

    private Future<SkillManifest> loadFromJar(String jarPath) {
        return vertx.executeBlocking(() -> {
            try (JarFile jar = new JarFile(jarPath)) {
                JarEntry entry = jar.getJarEntry("SKILL.md");
                if (entry == null) {
                    log.warn("JAR skill at {} missing SKILL.md", jarPath);
                    return null;
                }

                try (InputStream is = jar.getInputStream(entry)) {
                    String content = new String(is.readAllBytes());
                    String filename = Path.of(jarPath).getFileName().toString();
                    String folderId = filename.substring(0, filename.length() - 4);
                    return SkillManifest.fromMarkdown(folderId, content, null, jarPath);
                }
            } catch (Exception e) {
                log.error("Failed to load skill from JAR {}: {}", jarPath, e.getMessage());
                return null;
            }
        });
    }
}
