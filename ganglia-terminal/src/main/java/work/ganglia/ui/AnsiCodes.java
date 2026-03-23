package work.ganglia.ui;

/** Shared ANSI escape sequence helpers for the terminal UI. */
public final class AnsiCodes {

  private AnsiCodes() {}

  /** Moves the cursor to {@code row} (1-based) column 1 and clears the entire line. */
  public static String moveAndClear(int row) {
    return String.format("\033[%d;1H\033[2K", row);
  }

  /** Formats a duration in milliseconds as a human-readable string (e.g. "42ms", "1.3s"). */
  public static String formatDuration(long millis) {
    if (millis < 1000) {
      return millis + "ms";
    }
    double secs = millis / 1000.0;
    return String.format("%.1fs", secs);
  }
}
