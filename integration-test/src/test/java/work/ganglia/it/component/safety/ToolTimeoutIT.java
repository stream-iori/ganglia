package work.ganglia.it.component.safety;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.it.support.MockModelIT;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.llm.ChatRequest;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.internal.state.TokenUsage;

/** Integration tests for tool execution timeout handling. */
public class ToolTimeoutIT extends MockModelIT {

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
