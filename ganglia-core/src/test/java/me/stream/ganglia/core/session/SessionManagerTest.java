package me.stream.ganglia.core.session;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import me.stream.ganglia.core.model.Message;
import me.stream.ganglia.core.model.Role;
import me.stream.ganglia.core.model.SessionContext;
import me.stream.ganglia.core.state.LogManager;
import me.stream.ganglia.core.state.StateEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
class SessionManagerTest {

    @Mock
    StateEngine stateEngine;
    @Mock
    LogManager logManager;
    @Mock
    me.stream.ganglia.core.config.ConfigManager configManager;

    SessionManager sessionManager;

    @BeforeEach
    void setUp() {
        sessionManager = new DefaultSessionManager(stateEngine, logManager, configManager);
    }

    @Test
    void testGetSessionNew(VertxTestContext testContext) {
        when(stateEngine.loadSession("new-id")).thenReturn(Future.failedFuture("not found"));

        sessionManager.getSession("new-id").onComplete(testContext.succeeding(context -> {
            assertEquals("new-id", context.sessionId());
            assertNull(context.currentTurn());
            testContext.completeNow();
        }));
    }

    @Test
    void testPersist(VertxTestContext testContext) {
        SessionContext context = sessionManager.createSession("id");
        when(stateEngine.saveSession(context)).thenReturn(Future.succeededFuture());
        when(logManager.appendLog(context)).thenReturn(Future.succeededFuture());

        sessionManager.persist(context).onComplete(testContext.succeeding(v -> {
            verify(stateEngine).saveSession(context);
            verify(logManager).appendLog(context);
            testContext.completeNow();
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
