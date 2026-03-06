package work.ganglia.infrastructure.internal.state;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.kernel.todo.ToDoList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(VertxExtension.class)
class FileStateEngineTest {

    private Vertx vertx;
    private FileStateEngine stateEngine;
    private static final String TEST_STATE_DIR = ".ganglia/state";

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext) {
        this.vertx = vertx;
        this.stateEngine = new FileStateEngine(vertx);
        testContext.completeNow();
    }

    @AfterEach
    void tearDown(Vertx vertx, VertxTestContext testContext) {
        vertx.fileSystem().deleteRecursive(TEST_STATE_DIR)
                .onComplete(ar -> testContext.completeNow());
    }

    @Test
    void testSaveAndLoadSession(VertxTestContext testContext) {
        String sessionId = UUID.randomUUID().toString();
        SessionContext context = new SessionContext(sessionId, Collections.emptyList(), null, Collections.emptyMap(), Collections.emptyList(), null, ToDoList.empty());

        stateEngine.saveSession(context)
                .compose(v -> stateEngine.loadSession(sessionId))
                .onComplete(testContext.succeeding(loadedContext -> {
                    testContext.verify(() -> {
                        assertNotNull(loadedContext);
                        assertEquals(sessionId, loadedContext.sessionId());
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void testLoadNonExistentSession(VertxTestContext testContext) {
        String sessionId = "non-existent-" + UUID.randomUUID();

        stateEngine.loadSession(sessionId)
                .onComplete(testContext.failing(err -> {
                    testContext.verify(() -> {
                        assertNotNull(err);
                        testContext.completeNow();
                    });
                }));
    }
}
