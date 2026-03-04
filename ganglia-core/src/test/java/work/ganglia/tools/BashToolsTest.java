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
    void testRunShellCommand(VertxTestContext testContext) {
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
        // MAX_OUTPUT_SIZE is 8KB. Generate 10KB of output.
        // Use a simple loop to generate exactly 10,000 'A' characters.
        String command = "for i in {1..1000}; do printf 'AAAAAAAAAA'; done";

        tools.execute("run_shell_command", Map.of("command", command), context)
            .onComplete(testContext.succeeding(result -> {
                testContext.verify(() -> {
                    assertEquals(ToolInvokeResult.Status.EXCEPTION, result.status());
                    assertNotNull(result.errorDetails());
                    assertEquals(ToolErrorResult.ErrorType.SIZE_LIMIT_EXCEEDED, result.errorDetails().errorType());
                    // The partial output should be 8KB
                    assertNotNull(result.errorDetails().partialOutput());
                    assertEquals(8192, result.errorDetails().partialOutput().length());
                    testContext.completeNow();
                });
            }));
    }
}
