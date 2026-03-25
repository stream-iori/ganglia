package work.ganglia.infrastructure.internal.skill;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.file.FileSystem;

import work.ganglia.port.internal.skill.SkillLoader;
import work.ganglia.port.internal.skill.SkillManifest;

public class FileSystemSkillLoader implements SkillLoader {
  private static final Logger log = LoggerFactory.getLogger(FileSystemSkillLoader.class);

  private final Vertx vertx;
  private final List<Path> baseDirs;

  public FileSystemSkillLoader(Vertx vertx, List<Path> baseDirs) {
    this.vertx = vertx;
    this.baseDirs = baseDirs;
  }

  @Override
  public Future<List<SkillManifest>> load() {
    List<Future<List<SkillManifest>>> futures = new ArrayList<>();
    for (Path dir : baseDirs) {
      futures.add(scanDirectory(dir));
    }
    return Future.join(futures)
        .map(
            composite -> {
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
        .compose(
            exists -> {
              if (!exists) {
                return Future.succeededFuture(List.of());
              }
              return fs.readDir(baseDir.toString())
                  .compose(
                      list -> {
                        List<Future<SkillManifest>> futures = new ArrayList<>();
                        for (String skillPath : list) {
                          futures.add(loadSingleSkill(skillPath));
                        }
                        return Future.join(futures)
                            .map(
                                composite -> {
                                  List<SkillManifest> loaded = new ArrayList<>();
                                  for (int i = 0; i < futures.size(); i++) {
                                    SkillManifest m = composite.resultAt(i);
                                    if (m != null) {
                                      loaded.add(m);
                                    }
                                  }
                                  return loaded;
                                });
                      });
            });
  }

  private Future<SkillManifest> loadSingleSkill(String skillPath) {
    FileSystem fs = vertx.fileSystem();
    String skillMdPath = skillPath + "/SKILL.md";
    String skillJsonPath = skillPath + "/skill.json";
    String absoluteSkillDir = Paths.get(skillPath).toAbsolutePath().toString();

    return fs.exists(skillMdPath)
        .compose(
            mdExists -> {
              if (mdExists) {
                return fs.readFile(skillMdPath)
                    .map(
                        buffer -> {
                          String folderName = Paths.get(skillPath).getFileName().toString();
                          return SkillManifest.fromMarkdown(
                              folderName, buffer.toString(), absoluteSkillDir, null);
                        });
              } else {
                return fs.exists(skillJsonPath)
                    .compose(
                        jsonExists -> {
                          if (!jsonExists) {
                            return Future.succeededFuture(null);
                          }
                          return fs.readFile(skillJsonPath)
                              .map(buffer -> SkillManifest.fromJson(buffer.toJsonObject()));
                        });
              }
            });
  }
}
