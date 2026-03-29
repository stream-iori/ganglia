package work.ganglia.infrastructure.external.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.infrastructure.external.tool.model.ToolInvokeResult;
import work.ganglia.infrastructure.external.tool.util.LocalCommandExecutor;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.stubs.StubExecutionContext;

@ExtendWith(VertxExtension.class)
class BashToolsTest {

  private BashTools tools;
  private SessionContext context;

  @BeforeEach
  void setUp(Vertx vertx) {
    tools = new BashTools(new LocalCommandExecutor(vertx));
    context =
        new SessionContext(
            UUID.randomUUID().toString(),
            Collections.emptyList(),
            null,
            Collections.emptyMap(),
            Collections.emptyList(),
            null);
  }

  @Test
  void testRunShellCommand(Vertx vertx, VertxTestContext testContext) {
    String sessionId = context.sessionId();
    StubExecutionContext execContext = new StubExecutionContext(sessionId);

    tools
        .execute("run_shell_command", Map.of("command", "echo 'hello world'"), context, execContext)
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertEquals(ToolInvokeResult.Status.SUCCESS, result.status());
                        assertTrue(result.output().contains("hello world"));
                        // Verify streaming emission
                        assertTrue(
                            execContext.getStreams().stream()
                                .anyMatch(s -> s.contains("hello world")));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testSizeLimitExceeded(VertxTestContext testContext) {
    // MAX_OUTPUT_SIZE is 128KB. Generate 130KB of output.
    String command = "printf 'A%.s' {1..133120}"; // 130 * 1024 = 133120
    tools
        .execute(
            "run_shell_command", Map.of("command", command), context, new StubExecutionContext())
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        // VertxProcess signals size-limit by completing with exit code 1 and
                        // prepending an [OUTPUT TRUNCATED] marker — it does not fail the promise.
                        // CommandResultHandler.fromResult maps exit code 1 → ERROR status.
                        assertEquals(ToolInvokeResult.Status.ERROR, result.status());
                        assertTrue(
                            result.output().contains("[OUTPUT TRUNCATED"),
                            "Output must contain the truncation marker");
                        testContext.completeNow();
                      });
                }));
  }
}
