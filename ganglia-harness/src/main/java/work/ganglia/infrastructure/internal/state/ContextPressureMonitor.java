package work.ganglia.infrastructure.internal.state;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.internal.prompt.ContextBudget;
import work.ganglia.port.internal.state.ContextEventPublisher;
import work.ganglia.util.TokenCounter;

/**
 * Monitors context pressure and emits warnings before compression is needed.
 *
 * <p>Four pressure levels:
 *
 * <ul>
 *   <li>NORMAL: < 60% - No action needed
 *   <li>WARNING: 60-80% - Consider slimming, UI may show indicator
 *   <li>CRITICAL: 80-95% - Compression will trigger soon
 *   <li>BLOCKING: > 95% - Must compress before continuing
 * </ul>
 */
public class ContextPressureMonitor {
  private static final Logger logger = LoggerFactory.getLogger(ContextPressureMonitor.class);

  // Threshold constants (percentages of history budget)
  private static final double WARNING_THRESHOLD = 0.60;
  private static final double CRITICAL_THRESHOLD = 0.80;
  private static final double BLOCKING_THRESHOLD = 0.95;

  private final ContextBudget budget;
  private final TokenCounter tokenCounter;
  private final ContextEventPublisher eventPublisher;

  // Track last emitted level to avoid duplicate events
  private ContextPressure.Level lastEmittedLevel = null;

  public ContextPressureMonitor(ContextBudget budget, TokenCounter tokenCounter) {
    this(budget, tokenCounter, null);
  }

  public ContextPressureMonitor(ContextBudget budget, TokenCounter tokenCounter, ContextEventPublisher eventPublisher) {
    this.budget = budget;
    this.tokenCounter = tokenCounter;
    this.eventPublisher = eventPublisher;
  }

  /**
   * Evaluates current context pressure.
   *
   * @param context the session context to evaluate
   * @return the current pressure level with details
   */
  public ContextPressure evaluate(SessionContext context) {
    int usedTokens = context.history().stream().mapToInt(m -> m.countTokens(tokenCounter)).sum();

    int budgetTokens = budget.history();
    double percentUsed = budgetTokens > 0 ? (double) usedTokens / budgetTokens : 0.0;

    ContextPressure.Level level;
    if (percentUsed >= BLOCKING_THRESHOLD) {
      level = ContextPressure.Level.BLOCKING;
    } else if (percentUsed >= CRITICAL_THRESHOLD) {
      level = ContextPressure.Level.CRITICAL;
    } else if (percentUsed >= WARNING_THRESHOLD) {
      level = ContextPressure.Level.WARNING;
    } else {
      level = ContextPressure.Level.NORMAL;
    }

    return new ContextPressure(level, usedTokens, budgetTokens, percentUsed);
  }

  /**
   * Evaluates and emits observation if level changed. Returns the pressure for caller to act upon.
   *
   * @param context the session context to evaluate
   * @return the current pressure level with details
   */
  public ContextPressure evaluateAndNotify(SessionContext context) {
    ContextPressure pressure = evaluate(context);

    if (pressure.level() != lastEmittedLevel) {
      lastEmittedLevel = pressure.level();

      if (eventPublisher != null && pressure.level() != ContextPressure.Level.NORMAL) {
        eventPublisher.publishPressureChanged(
            context.sessionId(),
            pressure.level().name(),
            pressure.usedTokens(),
            pressure.budgetTokens());

        logger.info(
            "Context pressure changed to {}: {} / {} tokens ({}%)",
            pressure.level(),
            pressure.usedTokens(),
            pressure.budgetTokens(),
            (int) (pressure.percentUsed() * 100));
      }
    }

    return pressure;
  }

  /** Resets the last emitted level (for new sessions). */
  public void reset() {
    lastEmittedLevel = null;
  }

  /** Returns the WARNING threshold (60%). */
  public static double getWarningThreshold() {
    return WARNING_THRESHOLD;
  }

  /** Returns the CRITICAL threshold (80%). */
  public static double getCriticalThreshold() {
    return CRITICAL_THRESHOLD;
  }

  /** Returns the BLOCKING threshold (95%). */
  public static double getBlockingThreshold() {
    return BLOCKING_THRESHOLD;
  }
}
