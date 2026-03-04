package work.ganglia.core.session;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import work.ganglia.core.model.Message;
import work.ganglia.core.model.SessionContext;
import work.ganglia.stubs.InMemoryLogManager;
import work.ganglia.stubs.InMemoryStateEngine;
import work.ganglia.stubs.StubConfigManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
class SessionManagerTest {

    InMemoryStateEngine stateEngine;
    InMemoryLogManager logManager;
    StubConfigManager configManager;
    SessionManager sessionManager;
    Vertx vertx;

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx();
        stateEngine = new InMemoryStateEngine();
        logManager = new InMemoryLogManager();
        configManager = new StubConfigManager(vertx);
        sessionManager = new DefaultSessionManager(stateEngine, logManager, configManager);
    }

    @Test
    void testGetSessionNew(VertxTestContext testContext) {
        // No pre-loading, so it should return new session
        sessionManager.getSession("new-id").onComplete(testContext.succeeding(context -> {
            assertEquals("new-id", context.sessionId());
            assertNull(context.currentTurn());
            testContext.completeNow();
        }));
    }

    @Test
    void testPersist(VertxTestContext testContext) {
        SessionContext context = sessionManager.createSession("id");

        sessionManager.persist(context).onComplete(testContext.succeeding(v -> {
            testContext.verify(() -> {
                // Verify it is in state engine
                assertTrue(stateEngine.getSessions().containsKey("id"));
                // Verify it is in log manager
                assertEquals(1, logManager.getLogs().size());
                assertEquals("id", logManager.getLogs().get(0).sessionId());
                testContext.completeNow();
            });
        }));
    }

    @Test
    void testTurnManagement() {
        SessionContext context = sessionManager.createSession("id");

        // 1. Start Turn
        Message userMsg = Message.user("hello");
        context = sessionManager.startTurn(context, userMsg);
        assertNotNull(context.currentTurn());
        assertEquals("hello", context.currentTurn().userMessage().content());

        // 2. Add Step
        Message thought = Message.assistant("thinking...");
        context = sessionManager.addStep(context, thought);
        assertEquals(1, context.currentTurn().intermediateSteps().size());
        assertEquals("thinking...", context.currentTurn().intermediateSteps().get(0).content());

        // 3. Complete Turn
        Message response = Message.assistant("hi!");
        context = sessionManager.completeTurn(context, response);
        assertNotNull(context.currentTurn().finalResponse());
        assertEquals("hi!", context.currentTurn().finalResponse().content());
    }
}
