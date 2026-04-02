package work.ganglia.port.internal.state;

import work.ganglia.port.chat.SessionContext;

/**
 * Result of applying an optimization step.
 *
 * @param context the potentially modified session context
 * @param changed whether this step made any changes
 * @param tokensSaved number of tokens saved by this optimization (0 if no change)
 * @param skipRemaining whether to skip remaining steps (e.g., for hard limit abort)
 */
public record OptimizationResult(
    SessionContext context, boolean changed, int tokensSaved, boolean skipRemaining) {

  /** Creates a result indicating no changes were made. */
  public static OptimizationResult unchanged(SessionContext context) {
    return new OptimizationResult(context, false, 0, false);
  }

  /** Creates a result indicating changes were made. */
  public static OptimizationResult changed(SessionContext context, int tokensSaved) {
    return new OptimizationResult(context, true, tokensSaved, false);
  }

  /** Creates a result that skips remaining steps. */
  public static OptimizationResult skipRemaining(SessionContext context) {
    return new OptimizationResult(context, false, 0, true);
  }

  /** Creates a result that both changed and skips remaining. */
  public static OptimizationResult changedAndSkip(SessionContext context, int tokensSaved) {
    return new OptimizationResult(context, true, tokensSaved, true);
  }
}
