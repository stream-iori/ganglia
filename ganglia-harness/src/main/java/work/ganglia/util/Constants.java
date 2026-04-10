package work.ganglia.util;

/** Central repository for cross-cutting constants in Ganglia. */
public final class Constants {
  private Constants() {}

  // --- EventBus Addresses ---
  public static final String ADDRESS_MEMORY_EVENT = "ganglia.memory.event";
  public static final String ADDRESS_USAGE_RECORD = "ganglia.usage.record";
  public static final String ADDRESS_USAGE_ESTIMATE = "ganglia.usage.estimate";
  public static final String ADDRESS_UI_STREAM_PREFIX = "ganglia.ui.stream.";
  public static final String SUFFIX_TTY = ".tty";
  public static final String ADDRESS_UI_OUTBOUND_CACHE = "ganglia.ui.stream.outbound.cache";
  public static final String ADDRESS_OBSERVATIONS_PREFIX = "ganglia.observations.";
  public static final String ADDRESS_OBSERVATIONS_ALL = "ganglia.observations.all";
  public static final String ADDRESS_USAGE_UPDATE = "ganglia.usage.update.";

  // --- File Paths & Names ---
  public static final String DEFAULT_GANGLIA_DIR = ".ganglia";
  public static final String DEFAULT_CONFIG_FILE = ".ganglia/config.json";
  public static final String DIR_STATE = ".ganglia/state";
  public static final String DIR_LOGS = ".ganglia/logs";
  public static final String DIR_SKILLS = ".ganglia/skills";
  public static final String DIR_MEMORY = ".ganglia/memory";
  public static final String DIR_TRACE = ".ganglia/trace";
  public static final String DIR_TMP = ".ganglia/tmp";

  public static final String FILE_MEMORY_MD = ".ganglia/memory/MEMORY.md";
  public static final String FILE_USER_PROFILE_MD = ".ganglia/memory/USER.md";
  public static final String FILE_GANGLIA_MD = "GANGLIA.md";
  public static final String FILE_SKILL_MD = "SKILL.md";
}
