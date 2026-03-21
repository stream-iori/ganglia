package work.ganglia;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
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
}
