package work.ganglia.port.internal.skill;

import io.vertx.core.Future;
import java.util.List;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ToolSet;

/** Handles the runtime state and behavior of skills within a session. */
public interface SkillRuntime {
  /**
   * Activates a skill for the given context. Returns a new context with the skill added to active
   * IDs.
   */
  Future<SessionContext> activateSkill(String skillId, SessionContext context);

  /** Deactivates a skill for the given context. */
  Future<SessionContext> deactivateSkill(String skillId, SessionContext context);

  /** Builds the aggregated prompt content for all active skills in the context. */
  Future<String> getActiveSkillsPrompt(SessionContext context);

  /** Returns the tools provided by all active skills. */
  List<ToolSet> getActiveSkillsTools(SessionContext context);

  /**
   * Suggests skills that might be relevant for the current session state (e.g., working directory).
   */
  Future<String> suggestSkills(SessionContext context);
}
