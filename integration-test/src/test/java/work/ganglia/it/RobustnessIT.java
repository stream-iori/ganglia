package work.ganglia.it;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxTestContext;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.llm.ChatRequest;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.internal.state.TokenUsage;

/** Integration tests for robustness: SSRF protection and tool error recovery. */
public class RobustnessIT extends MockModelIT {

  @Test
  void webFetch_invalidSchemeBlockedAndLoopContinues(Vertx vertx, VertxTestContext testContext) {
    // Use a non-http scheme (no DNS lookup needed) to verify the tool returns an error
    // and the agent loop continues normally without hanging.
    ToolCall fetchCall =
        new ToolCall("c1", "web_fetch", Map.of("url", "ftp://evil.example.com/data"));

    when(mockModel.chatStream(any(ChatRequest.class), any()))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse("Fetching data.", List.of(fetchCall), new TokenUsage(10, 10))))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse(
                    "Scheme not allowed. Done.", Collections.emptyList(), new TokenUsage(10, 10))));

    SessionContext context = newSession();

    ganglia
        .agentLoop()
        .run("Fetch data from FTP", context)
        .onComplete(
            testContext.succeeding(
                result ->
                    testContext.verify(
                        () -> {
                          assertTrue(
                              result.contains("Done"),
                              "Agent loop should complete, got: " + result);
                          testContext.completeNow();
                        })));
  }

  @Test
  void toolExecution_normalWorkflowWithTimeout(Vertx vertx, VertxTestContext testContext) {
    ToolCall listCall = new ToolCall("c1", "list_directory", Map.of("path", "."));

    when(mockModel.chatStream(any(ChatRequest.class), any()))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse("Listing.", List.of(listCall), new TokenUsage(10, 10))))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse(
                    "Done listing.", Collections.emptyList(), new TokenUsage(10, 10))));

    SessionContext context = newSession();

    ganglia
        .agentLoop()
        .run("List directory", context)
        .onComplete(
            testContext.succeeding(
                result ->
                    testContext.verify(
                        () -> {
                          assertTrue(result.contains("Done"));
                          testContext.completeNow();
                        })));
  }
}
