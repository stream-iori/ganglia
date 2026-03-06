package work.ganglia.tools;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import work.ganglia.core.model.SessionContext;
import work.ganglia.tools.model.ToDoList;
import work.ganglia.tools.model.ToolErrorResult;
import work.ganglia.tools.model.ToolInvokeResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.json.JsonObject;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(VertxExtension.class)
class BashToolsTest {

    private BashTools tools;
    private SessionContext context;

    @BeforeEach
    void setUp(Vertx vertx) {
        tools = new BashTools(vertx);
        context = new SessionContext(UUID.randomUUID().toString(), Collections.emptyList(), null, Collections.emptyMap(), Collections.emptyList(), null, ToDoList.empty());
    }

    @Test
    void testRunShellCommand(Vertx vertx, VertxTestContext testContext) {
        String sessionId = context.sessionId();
        String expectedOutput = "hello world\n";
        
        // Listen for TTY event
        vertx.eventBus().<JsonObject>consumer("ganglia.ui.stream." + sessionId + ".tty", msg -> {
            testContext.verify(() -> {
                assertTrue(msg.body().getString("text").contains("hello world"));
            });
        });

        tools.execute("run_shell_command", Map.of("command", "echo 'hello world'"), context)
            .onComplete(testContext.succeeding(result -> {
                testContext.verify(() -> {
                    assertEquals(ToolInvokeResult.Status.SUCCESS, result.status());
                    assertTrue(result.output().contains("hello world"));
                    testContext.completeNow();
                });
            }));
    }

    @Test
    void testSizeLimitExceeded(VertxTestContext testContext) {
        // MAX_OUTPUT_SIZE is 128KB. Generate 130KB of output.
        String command = "printf 'A%.s' {1..133120}"; // 130 * 1024 = 133120

        tools.execute("run_shell_command", Map.of("command", command), context)
            .onComplete(testContext.succeeding(result -> {
                testContext.verify(() -> {
                    assertEquals(ToolInvokeResult.Status.EXCEPTION, result.status());
                    assertNotNull(result.errorDetails());
                    assertEquals(ToolErrorResult.ErrorType.SIZE_LIMIT_EXCEEDED, result.errorDetails().errorType());
                    // The partial output should be around 128KB
                    assertNotNull(result.errorDetails().partialOutput());
                    assertTrue(result.errorDetails().partialOutput().length() >= 131072);
                    testContext.completeNow();
                });
            }));
    }
}
