package work.ganglia.util;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;

import io.vertx.core.Future;
import io.vertx.core.Promise;

/**
 * A non-blocking concurrency limiter designed for the Vert.x event loop. Unlike {@link
 * java.util.concurrent.Semaphore}, this class never blocks the calling thread. When the concurrency
 * limit is reached, callers are queued and their futures complete in FIFO order as slots free up.
 *
 * <p>Thread safety: this class is intended to be used exclusively from a single Vert.x event loop
 * thread. No internal synchronization is provided.
 */
public class AsyncConcurrencyLimiter {
  private final int maxConcurrent;
  private int active;
  private final Deque<Promise<Void>> waitQueue = new ArrayDeque<>();

  public AsyncConcurrencyLimiter(int maxConcurrent) {
    if (maxConcurrent <= 0) {
      throw new IllegalArgumentException("maxConcurrent must be positive, got " + maxConcurrent);
    }
    this.maxConcurrent = maxConcurrent;
  }

  /**
   * Acquires a slot. Returns an immediately-succeeded future if a slot is available, or a pending
   * future that will complete when a slot is released.
   */
  public Future<Void> acquire() {
    if (active < maxConcurrent) {
      active++;
      return Future.succeededFuture();
    }
    Promise<Void> promise = Promise.promise();
    waitQueue.addLast(promise);
    return promise.future();
  }

  /** Releases a slot, completing the next queued waiter if any. */
  public void release() {
    if (!waitQueue.isEmpty()) {
      waitQueue.removeFirst().complete();
    } else {
      active--;
    }
  }

  /**
   * Convenience method: acquires a slot, runs the supplier, and releases on completion (success or
   * failure). If the supplier throws synchronously, the slot is still released.
   */
  public <T> Future<T> withLimit(Supplier<Future<T>> futureSupplier) {
    return acquire()
        .compose(
            v -> {
              try {
                return futureSupplier.get().onComplete(r -> release());
              } catch (Exception e) {
                release();
                return Future.failedFuture(e);
              }
            });
  }

  /** Returns the number of active slots in use. Visible for testing. */
  public int active() {
    return active;
  }

  /** Returns the number of waiters in the queue. Visible for testing. */
  public int waiting() {
    return waitQueue.size();
  }
}
