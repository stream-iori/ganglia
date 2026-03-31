package work.ganglia.infrastructure.external.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.port.external.llm.ChatRequest;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.internal.state.ExecutionContext;

@ExtendWith(VertxExtension.class)
class AbstractModelGatewaySemaphoreTest {

  /** Minimal concrete subclass that exposes withLimit for testing. */
  static class TestGateway extends AbstractModelGateway {
    TestGateway(Vertx vertx) {
      super(vertx);
    }

    @Override
    public Future<ModelResponse> chat(ChatRequest request) {
      return Future.failedFuture("not implemented");
    }

    @Override
    public Future<ModelResponse> chatStream(ChatRequest request, ExecutionContext context) {
      return Future.failedFuture("not implemented");
    }

    /** Expose withLimit for testing. */
    public <T> Future<T> testWithLimit(java.util.function.Supplier<Future<T>> supplier) {
      return withLimit(supplier);
    }
  }

  @Test
  void sixthCallQueuesAndCompletesAfterRelease(Vertx vertx, VertxTestContext testContext) {
    TestGateway gw = new TestGateway(vertx);

    // Exhaust all 5 slots with never-completing futures
    Promise<String>[] holders = new Promise[5];
    for (int i = 0; i < 5; i++) {
      holders[i] = Promise.promise();
      final int idx = i;
      gw.testWithLimit(() -> holders[idx].future());
    }

    // 6th call: should be queued (not failed)
    AtomicBoolean supplierCalled = new AtomicBoolean(false);
    Future<String> result =
        gw.testWithLimit(
            () -> {
              supplierCalled.set(true);
              return Future.succeededFuture("queued-result");
            });

    // Should not have completed yet — it's waiting for a slot
    assertFalse(result.isComplete(), "6th call should be queued, not immediately resolved");
    assertFalse(supplierCalled.get(), "Supplier should not be called while queued");

    // Release one holder → 6th call should now execute
    holders[0].complete("done");

    result.onComplete(
        testContext.succeeding(
            val ->
                testContext.verify(
                    () -> {
                      assertTrue(supplierCalled.get(), "Supplier should have been called");
                      assertEquals("queued-result", val);
                      // Clean up remaining holders
                      for (int i = 1; i < holders.length; i++) holders[i].complete("done");
                      testContext.completeNow();
                    })));
  }

  @Test
  void permitReleasedOnSuccess(Vertx vertx, VertxTestContext testContext) {
    TestGateway gw = new TestGateway(vertx);
    AtomicInteger callCount = new AtomicInteger(0);

    // Run 6 calls sequentially — all should succeed since each releases its slot
    Future<String> chain = Future.succeededFuture();
    for (int i = 0; i < 6; i++) {
      chain =
          chain.compose(
              v ->
                  gw.testWithLimit(
                      () -> {
                        callCount.incrementAndGet();
                        return Future.succeededFuture("ok");
                      }));
    }

    chain.onComplete(
        testContext.succeeding(
            v ->
                testContext.verify(
                    () -> {
                      assertEquals(6, callCount.get(), "All 6 calls should have succeeded");
                      testContext.completeNow();
                    })));
  }

  @Test
  void permitReleasedOnFailure(Vertx vertx, VertxTestContext testContext) {
    TestGateway gw = new TestGateway(vertx);

    // Fail a call, then verify the slot was released by succeeding another
    gw.testWithLimit(() -> Future.<String>failedFuture("boom"))
        .recover(err -> gw.testWithLimit(() -> Future.succeededFuture("recovered")))
        .onComplete(
            testContext.succeeding(
                result ->
                    testContext.verify(
                        () -> {
                          assertEquals("recovered", result);
                          testContext.completeNow();
                        })));
  }

  @Test
  void permitReleasedOnSupplierException(Vertx vertx, VertxTestContext testContext) {
    TestGateway gw = new TestGateway(vertx);

    // Supplier throws synchronously
    gw.<String>testWithLimit(
            () -> {
              throw new RuntimeException("sync boom");
            })
        .recover(
            err ->
                // Should still be able to acquire slot
                gw.testWithLimit(() -> Future.succeededFuture("after-exception")))
        .onComplete(
            testContext.succeeding(
                result ->
                    testContext.verify(
                        () -> {
                          assertEquals("after-exception", result);
                          testContext.completeNow();
                        })));
  }
}
