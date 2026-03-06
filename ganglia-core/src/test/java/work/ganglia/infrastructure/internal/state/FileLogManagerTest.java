package work.ganglia.infrastructure.internal.state;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import work.ganglia.port.chat.Message;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.kernel.todo.ToDoList;
import work.ganglia.port.chat.Turn;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(VertxExtension.class)
class FileLogManagerTest {

    private Vertx vertx;
    private FileLogManager logManager;
    private static final String LOG_DIR = ".ganglia/logs";

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext) {
        this.vertx = vertx;
        this.logManager = new FileLogManager(vertx);
        testContext.completeNow();
    }

    @AfterEach
    void tearDown(Vertx vertx, VertxTestContext testContext) {
        vertx.fileSystem().deleteRecursive(LOG_DIR)
                .onComplete(ar -> testContext.completeNow());
    }

    @Test
    void testAppendLog(VertxTestContext testContext) {
        String content = "Test log content " + UUID.randomUUID();
        Message msg = Message.user(content);
        Turn turn = new Turn("turn-1", msg, new ArrayList<>(), null);

        SessionContext context = new SessionContext("session-1", Collections.emptyList(), turn, Collections.emptyMap(), Collections.emptyList(), null, ToDoList.empty());

        logManager.appendLog(context)
                .compose(v -> {
                    String filename = LOG_DIR + "/" + LocalDate.now().format(DateTimeFormatter.ISO_DATE) + ".md";
                    return vertx.fileSystem().readFile(filename);
                })
                .onComplete(testContext.succeeding(buffer -> {
                    testContext.verify(() -> {
                        assertTrue(buffer.toString().contains(content));
                        testContext.completeNow();
                    });
                }));
    }
}
