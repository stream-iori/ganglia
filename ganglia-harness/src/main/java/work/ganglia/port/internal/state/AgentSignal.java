package work.ganglia.port.internal.state;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A signal used to propagate a hard abort request through the agent loop and underlying operations.
 * Supports registering callbacks that are executed immediately when an abort is triggered.
 *
 * <p>Thread safety: {@link #abort()} may be called from any thread (e.g. JVM signal handler for
 * Ctrl+C). {@link #onAbort(Runnable)} may be called concurrently. The implementation uses {@link
 * AtomicBoolean} for the flag and {@link CopyOnWriteArrayList} for callbacks, avoiding {@code
 * synchronized} blocks entirely.
 */
public class AgentSignal {
  private final AtomicBoolean aborted = new AtomicBoolean(false);
  private final CopyOnWriteArrayList<Runnable> callbacks = new CopyOnWriteArrayList<>();
  private volatile AbortReason reason;

  /** Reasons why an agent execution was aborted. */
  public enum AbortReason {
    /** User pressed Ctrl+C or explicitly cancelled. */
    USER_CANCELLED,
    /** A blackboard fact invalidated the mission. */
    MISSION_SUPERSEDED,
    /** Token or cycle budget exhausted. */
    BUDGET_EXCEEDED,
    /** No progress detected in N cycles. */
    STALL_DETECTED
  }

  public AgentSignal() {}

  /** Triggers the abort signal with USER_CANCELLED reason and executes all registered callbacks. */
  public void abort() {
    abort(AbortReason.USER_CANCELLED);
  }

  /** Triggers the abort signal with the given reason and executes all registered callbacks. */
  public void abort(AbortReason reason) {
    if (aborted.compareAndSet(false, true)) {
      this.reason = reason;
      for (Runnable callback : callbacks) {
        try {
          callback.run();
        } catch (Exception e) {
          // Ignore callback errors during abort to ensure all callbacks are attempted
        }
      }
      callbacks.clear();
    }
  }

  /** Returns the abort reason, or null if not yet aborted. */
  public AbortReason getReason() {
    return reason;
  }

  /**
   * Registers a callback to be executed when the signal is aborted. If the signal is already
   * aborted, the callback is executed immediately. Uses a double-check pattern to prevent the race
   * where abort() fires between the check and the add.
   */
  public void onAbort(Runnable callback) {
    callbacks.add(callback);
    // Double-check: if abort() raced ahead, run and remove the callback ourselves
    if (aborted.get()) {
      if (callbacks.remove(callback)) {
        callback.run();
      }
    }
  }

  public boolean isAborted() {
    return aborted.get();
  }
}
