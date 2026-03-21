package work.ganglia.port.internal.state;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A signal used to propagate a hard abort request through the agent loop and underlying operations.
 * Supports registering callbacks that are executed immediately when an abort is triggered.
 */
public class AgentSignal {
  private final AtomicBoolean aborted = new AtomicBoolean(false);
  private final List<Runnable> callbacks = new ArrayList<>();

  public AgentSignal() {}

  /** Triggers the abort signal and executes all registered callbacks. */
  public synchronized void abort() {
    if (aborted.compareAndSet(false, true)) {
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

  /**
   * Registers a callback to be executed when the signal is aborted. If the signal is already
   * aborted, the callback is executed immediately.
   */
  public synchronized void onAbort(Runnable callback) {
    if (aborted.get()) {
      callback.run();
    } else {
      callbacks.add(callback);
    }
  }

  public boolean isAborted() {
    return aborted.get();
  }
}
