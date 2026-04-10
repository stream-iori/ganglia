package work.ganglia.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.vertx.core.Future;
import io.vertx.core.Promise;

class AsyncConcurrencyLimiterTest {

  @Test
  void acquireSucceedsWhenUnderLimit() {
    AsyncConcurrencyLimiter limiter = new AsyncConcurrencyLimiter(2);

    Future<Void> f1 = limiter.acquire();
    Future<Void> f2 = limiter.acquire();

    assertTrue(f1.isComplete());
    assertTrue(f2.isComplete());
    assertEquals(2, limiter.active());
    assertEquals(0, limiter.waiting());
  }

  @Test
  void acquireQueuesWhenAtLimit() {
    AsyncConcurrencyLimiter limiter = new AsyncConcurrencyLimiter(1);

    Future<Void> f1 = limiter.acquire();
    Future<Void> f2 = limiter.acquire();

    assertTrue(f1.isComplete());
    assertFalse(f2.isComplete());
    assertEquals(1, limiter.active());
    assertEquals(1, limiter.waiting());

    // Release first slot → second waiter should complete
    limiter.release();
    assertTrue(f2.isComplete());
    assertTrue(f2.succeeded());
    assertEquals(1, limiter.active());
    assertEquals(0, limiter.waiting());
  }

  @Test
  void fifoOrdering() {
    AsyncConcurrencyLimiter limiter = new AsyncConcurrencyLimiter(1);
    limiter.acquire(); // fill the slot

    List<Integer> order = new ArrayList<>();
    Future<Void> f1 = limiter.acquire();
    f1.onSuccess(v -> order.add(1));
    Future<Void> f2 = limiter.acquire();
    f2.onSuccess(v -> order.add(2));
    Future<Void> f3 = limiter.acquire();
    f3.onSuccess(v -> order.add(3));

    limiter.release(); // completes f1
    limiter.release(); // completes f2
    limiter.release(); // completes f3

    assertEquals(List.of(1, 2, 3), order);
  }

  @Test
  void withLimitReleasesOnSuccess() {
    AsyncConcurrencyLimiter limiter = new AsyncConcurrencyLimiter(1);

    Future<String> result = limiter.withLimit(() -> Future.succeededFuture("ok"));
    assertTrue(result.isComplete());
    assertEquals("ok", result.result());
    assertEquals(0, limiter.active());
  }

  @Test
  void withLimitReleasesOnFailure() {
    AsyncConcurrencyLimiter limiter = new AsyncConcurrencyLimiter(1);

    Future<String> result = limiter.withLimit(() -> Future.failedFuture("boom"));
    assertTrue(result.isComplete());
    assertTrue(result.failed());
    assertEquals(0, limiter.active());
  }

  @Test
  void withLimitReleasesOnSupplierException() {
    AsyncConcurrencyLimiter limiter = new AsyncConcurrencyLimiter(1);

    Future<String> result =
        limiter.withLimit(
            () -> {
              throw new RuntimeException("sync boom");
            });
    assertTrue(result.isComplete());
    assertTrue(result.failed());
    assertEquals(0, limiter.active());
  }

  @Test
  void withLimitQueuesWhenFull() {
    AsyncConcurrencyLimiter limiter = new AsyncConcurrencyLimiter(1);

    Promise<String> holder = Promise.promise();
    Future<String> first = limiter.withLimit(holder::future);
    assertFalse(first.isComplete());
    assertEquals(1, limiter.active());

    // Second call should queue
    Future<String> second = limiter.withLimit(() -> Future.succeededFuture("queued"));
    assertFalse(second.isComplete());
    assertEquals(1, limiter.waiting());

    // Complete the first → second should now execute and complete
    holder.complete("done");
    assertTrue(first.isComplete());
    assertTrue(second.isComplete());
    assertEquals("queued", second.result());
    assertEquals(0, limiter.active());
  }

  @Test
  void rejectsNonPositiveMax() {
    assertThrows(IllegalArgumentException.class, () -> new AsyncConcurrencyLimiter(0));
    assertThrows(IllegalArgumentException.class, () -> new AsyncConcurrencyLimiter(-1));
  }
}
