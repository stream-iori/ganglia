package work.ganglia.port.internal.skill;

import java.util.List;

import io.vertx.core.Future;

/** Interface for loading skills from various sources (e.g., filesystem, classpath, remote). */
public interface SkillLoader {
  /** Loads all available skills from the source. */
  Future<List<SkillManifest>> load();
}
