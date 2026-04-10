package work.ganglia.port.internal.memory.model;

/**
 * Configuration for memory and skill nudge reminders.
 *
 * @param memoryNudgeInterval Remind agent to save memories every N turns (0 = disabled).
 * @param skillNudgeInterval Remind agent to create skills every N tool calls (0 = disabled).
 * @param flushMinTurns Minimum turns before triggering flush on session close (0 = disabled).
 */
public record NudgeConfig(int memoryNudgeInterval, int skillNudgeInterval, int flushMinTurns) {
  public static final NudgeConfig DEFAULT = new NudgeConfig(10, 15, 6);

  public boolean isMemoryNudgeEnabled() {
    return memoryNudgeInterval > 0;
  }

  public boolean isSkillNudgeEnabled() {
    return skillNudgeInterval > 0;
  }

  public boolean isFlushEnabled() {
    return flushMinTurns > 0;
  }
}
