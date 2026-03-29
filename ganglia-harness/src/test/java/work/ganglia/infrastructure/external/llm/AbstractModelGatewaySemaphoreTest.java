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

  /** Minimal concrete subclass that exposes withSemaphore for testing. */
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

    /** Expose withSemaphore for testing. */
    public <T> Future<T> testWithSemaphore(java.util.function.Supplier<Future<T>> supplier) {
      return withSemaphore(supplier);
    }
  }

  @Test
  void supplierNotCalledWhenPermitExhausted(Vertx vertx, VertxTestContext testContext) {
    TestGateway gw = new TestGateway(vertx);

    // Exhaust all 5 permits with never-completing futures
    Promise<String>[] holders = new Promise[5];
    for (int i = 0; i < 5; i++) {
      holders[i] = Promise.promise();
      final int idx = i;
      gw.testWithSemaphore(() -> holders[idx].future());
    }

    // 6th call: supplier should NOT be invoked
    AtomicBoolean supplierCalled = new AtomicBoolean(false);
    Future<String> result =
        gw.testWithSemaphore(
            () -> {
              supplierCalled.set(true);
              return Future.succeededFuture("should not happen");
            });

    result.onComplete(
        testContext.failing(
            err ->
                testContext.verify(
                    () -> {
                      assertFalse(
                          supplierCalled.get(),
                          "Supplier should not be called when permits exhausted");
                      assertTrue(err.getMessage().contains("Concurrency limit"));
                      // Clean up holders
                      for (Promise<String> h : holders) h.complete("done");
                      testContext.completeNow();
                    })));
  }

  @Test
  void permitReleasedOnSuccess(Vertx vertx, VertxTestContext testContext) {
    TestGateway gw = new TestGateway(vertx);
    AtomicInteger callCount = new AtomicInteger(0);

    // Run 6 calls sequentially — all should succeed since each releases its permit
    Future<String> chain = Future.succeededFuture();
    for (int i = 0; i < 6; i++) {
      chain =
          chain.compose(
              v ->
                  gw.testWithSemaphore(
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

    // Fail a call, then verify the permit was released by succeeding another
    gw.testWithSemaphore(() -> Future.<String>failedFuture("boom"))
        .recover(err -> gw.testWithSemaphore(() -> Future.succeededFuture("recovered")))
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
    gw.<String>testWithSemaphore(
            () -> {
              throw new RuntimeException("sync boom");
            })
        .recover(
            err ->
                // Should still be able to acquire permit
                gw.testWithSemaphore(() -> Future.succeededFuture("after-exception")))
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
