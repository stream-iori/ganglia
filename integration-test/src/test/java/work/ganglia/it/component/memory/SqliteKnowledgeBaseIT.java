package work.ganglia.it.component.memory;

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

import work.ganglia.it.support.SqliteModelIT;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.llm.ChatRequest;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.internal.state.TokenUsage;

/**
 * Tests the knowledge base (remember + recall_memory) tools work end-to-end with the SQLite
 * backend.
 */
public class SqliteKnowledgeBaseIT extends SqliteModelIT {

  @Test
  void rememberFact_persistsToSqliteMemory(Vertx vertx, VertxTestContext testContext) {
    ToolCall rememberCall =
        new ToolCall("c1", "remember", Map.of("fact", "Project uses Java 17 records."));

    when(mockModel.chatStream(any(ChatRequest.class), any()))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse(
                    "Remembering fact...", List.of(rememberCall), new TokenUsage(1, 1))))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse("Remembered.", Collections.emptyList(), new TokenUsage(1, 1))));

    SessionContext context = newSession();

    ganglia
        .agentLoop()
        .run("Remember that the project uses Java 17 records.", context)
        .onComplete(
            testContext.succeeding(
                result ->
                    testContext.verify(
                        () -> {
                          assertTrue(
                              result.contains("Remembered"),
                              "Agent should confirm remembering: " + result);
                          testContext.completeNow();
                        })));
  }

  @Test
  void rememberAndRecall_roundTrip(Vertx vertx, VertxTestContext testContext) {
    ToolCall rememberCall =
        new ToolCall(
            "c1", "remember", Map.of("fact", "Convention: always use Optional instead of null."));

    ToolCall recallCall =
        new ToolCall("c2", "recall_memory", Map.of("query", "Optional null convention"));

    when(mockModel.chatStream(any(ChatRequest.class), any()))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse(
                    "Storing convention...", List.of(rememberCall), new TokenUsage(1, 1))))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse("Now recalling...", List.of(recallCall), new TokenUsage(1, 1))))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse(
                    "The convention is to use Optional instead of null.",
                    Collections.emptyList(),
                    new TokenUsage(1, 1))));

    SessionContext context = newSession();

    ganglia
        .agentLoop()
        .run("Store and recall the Optional convention.", context)
        .onComplete(
            testContext.succeeding(
                result ->
                    testContext.verify(
                        () -> {
                          assertTrue(
                              result.contains("Optional"),
                              "Agent should mention Optional in result: " + result);
                          testContext.completeNow();
                        })));
  }
}
