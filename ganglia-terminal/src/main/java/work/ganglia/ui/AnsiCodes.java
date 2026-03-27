package work.ganglia.ui;

/** Shared ANSI escape sequence helpers for the terminal UI. */
public final class AnsiCodes {

  private AnsiCodes() {}

  /** Moves the cursor to {@code row} (1-based) column 1 and clears the entire line. */
  public static String moveAndClear(int row) {
    return String.format("\033[%d;1H\033[2K", row);
  }

  /** Moves the cursor to absolute row/col (both 1-based). */
  public static String moveTo(int row, int col) {
    return String.format("\033[%d;%dH", row, col);
  }

  /** Clears the entire current line (does not move the cursor). */
  public static String clearLine() {
    return "\033[2K";
  }

  /** Saves the current cursor position (ANSI save-cursor). */
  public static String saveCursor() {
    return "\033[s";
  }

  /** Restores the cursor to the last saved position. */
  public static String restoreCursor() {
    return "\033[u";
  }

  /** Shows the terminal cursor. */
  public static String showCursor() {
    return "\033[?25h";
  }

  /** Hides the terminal cursor. */
  public static String hideCursor() {
    return "\033[?25l";
  }

  /**
   * Sets the terminal scroll region to rows {@code top}..{@code bottom} (both 1-based inclusive).
   */
  public static String setScrollRegion(int top, int bottom) {
    return String.format("\033[%d;%dr", top, bottom);
  }

  /** Resets the terminal scroll region to the full screen. */
  public static String resetScrollRegion() {
    return "\033[r";
  }

  /** Moves the cursor up {@code n} rows. */
  public static String moveUp(int n) {
    return String.format("\033[%dA", n);
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
