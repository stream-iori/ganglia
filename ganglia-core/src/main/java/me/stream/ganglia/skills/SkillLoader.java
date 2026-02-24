package me.stream.ganglia.skills;

import io.vertx.core.Future;
import java.util.List;

/**
 * Interface for loading skills from various sources (e.g., filesystem, classpath, remote).
 */
public interface SkillLoader {
    /**
     * Loads all available skills from the source.
     */
    Future<List<SkillManifest>> load();
}
