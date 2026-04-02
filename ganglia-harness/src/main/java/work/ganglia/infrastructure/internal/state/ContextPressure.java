package work.ganglia.infrastructure.internal.state;

/** Represents the current context pressure level. */
public record ContextPressure(Level level, int usedTokens, int budgetTokens, double percentUsed) {

  /** Context pressure levels with increasing severity. */
  public enum Level {
    /** < 60% - Normal operation, no action needed */
    NORMAL,
    /** 60-80% - Warning, consider slimming, UI may show yellow indicator */
    WARNING,
    /** 80-95% - Critical, will trigger compression soon */
    CRITICAL,
    /** > 95% - Blocking, must compress before continuing */
    BLOCKING
  }

  /** Returns true if action is required (CRITICAL or BLOCKING). */
  public boolean requiresAction() {
    return level == Level.CRITICAL || level == Level.BLOCKING;
  }

  /** Returns true if this is a blocking level. */
  public boolean isBlocking() {
    return level == Level.BLOCKING;
  }

  /** Returns a human-readable description of the pressure level. */
  public String description() {
    return switch (level) {
      case NORMAL -> "Context usage is normal";
      case WARNING -> "Context usage is elevated, consider slimming";
      case CRITICAL -> "Context usage is critical, compression will trigger soon";
      case BLOCKING -> "Context usage is at maximum, compression required";
    };
  }
}
