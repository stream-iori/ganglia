package work.ganglia.port.internal.skill;

import java.util.List;
import java.util.Optional;

import io.vertx.core.Future;

/** Service for managing the lifecycle and registry of available skills. */
public interface SkillService {
  /** Initializes the service by loading available skills. */
  Future<Void> init();

  /** Lists all available skills. */
  List<SkillManifest> getAvailableSkills();

  /** Retrieves a specific skill by its ID. */
  Optional<SkillManifest> getSkill(String id);

  /** Reloads all skills from the loaders. */
  Future<Void> reload();
}
