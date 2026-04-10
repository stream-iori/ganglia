package work.ganglia;

import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.infrastructure.internal.state.DefaultSessionManager;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.stubs.InMemoryLogManager;
import work.ganglia.stubs.InMemoryStateEngine;
import work.ganglia.stubs.StubConfigManager;

/** Base class for tests that require a Vert.x instance and basic configuration stubs. */
@ExtendWith(VertxExtension.class)
public abstract class BaseGangliaTest {
  protected Vertx vertx;
  protected StubConfigManager configManager;
  protected DefaultSessionManager sessionManager;
  protected InMemoryStateEngine stateEngine;
  protected InMemoryLogManager logManager;

  @BeforeEach
  protected void setUpBase(Vertx vertx) {
    this.vertx = vertx;
    this.configManager = new StubConfigManager(vertx);
    this.stateEngine = new InMemoryStateEngine();
    this.logManager = new InMemoryLogManager();
    this.sessionManager = new DefaultSessionManager(stateEngine, logManager, configManager);
  }

  /** Helper to quickly create a mock session context. */
  protected SessionContext createSessionContext() {
    return sessionManager.createSession(UUID.randomUUID().toString());
  }

  protected SessionContext createSessionContext(String sessionId) {
    return sessionManager.createSession(sessionId);
  }

  /**
   * Helper to assert a successful Vert.x Future, verify the result, and complete the test context.
   */
  protected <T> void assertFutureSuccess(
      Future<T> future, VertxTestContext testContext, Consumer<T> assertions) {
    future.onComplete(
        testContext.succeeding(
            result ->
                testContext.verify(
                    () -> {
                      if (assertions != null) {
                        assertions.accept(result);
                      }
                      testContext.completeNow();
                    })));
  }

  /**
   * Helper to assert a failed Vert.x Future, verify the exception, and complete the test context.
   */
  protected <T> void assertFutureFailure(
      Future<T> future, VertxTestContext testContext, Consumer<Throwable> assertions) {
    future.onComplete(
        testContext.failing(
            error ->
                testContext.verify(
                    () -> {
                      if (assertions != null) {
                        assertions.accept(error);
                      }
                      testContext.completeNow();
                    })));
  }
}
