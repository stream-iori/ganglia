package work.ganglia.it.component.memory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.it.support.SqliteModelIT;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.llm.ChatRequest;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.internal.state.TokenUsage;

/** Tests the session search tools work with SQLite backend. */
public class SqliteSessionSearchIT extends SqliteModelIT {

  @Test
  void searchSessions_returnsResultsFromSqlite(Vertx vertx, VertxTestContext testContext) {
    ToolCall searchCall =
        new ToolCall("c1", "search_sessions", Map.of("query", "test", "limit", "5"));

    when(mockModel.chatStream(any(ChatRequest.class), any()))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse(
                    "Searching sessions...", List.of(searchCall), new TokenUsage(1, 1))))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse(
                    "No previous sessions found.", Collections.emptyList(), new TokenUsage(1, 1))));

    SessionContext context = newSession();

    ganglia
        .agentLoop()
        .run("Search for previous sessions about testing.", context)
        .onComplete(
            testContext.succeeding(
                result ->
                    testContext.verify(
                        () -> {
                          assertNotNull(result);
                          // The search should complete without errors even on empty database
                          assertFalse(
                              result.contains("Error"), "Should not contain errors: " + result);
                          testContext.completeNow();
                        })));
  }
}
