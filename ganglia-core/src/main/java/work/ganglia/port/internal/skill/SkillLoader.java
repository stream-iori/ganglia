package work.ganglia.port.internal.skill;

import io.vertx.core.Future;
import java.util.List;
import work.ganglia.port.internal.skill.SkillManifest;

/**
 * Interface for loading skills from various sources (e.g., filesystem, classpath, remote).
 */
public interface SkillLoader {
    /**
     * Loads all available skills from the source.
     */
    Future<List<SkillManifest>> load();
}
