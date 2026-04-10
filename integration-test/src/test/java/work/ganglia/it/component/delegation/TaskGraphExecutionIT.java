package work.ganglia.it.component.delegation;

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

public class TaskGraphExecutionIT extends MockModelIT {

  @Test
  void taskGraph_executesSequentialDag(Vertx vertx, VertxTestContext testContext) {
    ToolCall graphCall =
        new ToolCall(
            "c1",
            "propose_task_graph",
            Map.of(
                "nodes",
                List.of(
                    Map.of("id", "n1", "task", "Task 1", "persona", "GENERAL"),
                    Map.of(
                        "id",
                        "n2",
                        "task",
                        "Task 2",
                        "persona",
                        "GENERAL",
                        "dependencies",
                        List.of("n1"))),
                "approved",
                true));

    when(mockModel.chatStream(any(ChatRequest.class), any()))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse(
                    "Executing graph...", List.of(graphCall), new TokenUsage(10, 10))))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse("Task 1 Done", Collections.emptyList(), new TokenUsage(5, 5))))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse("Task 2 Done", Collections.emptyList(), new TokenUsage(5, 5))))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse(
                    "Graph completed successfully.",
                    Collections.emptyList(),
                    new TokenUsage(10, 10))));

    SessionContext context = newSession();

    ganglia
        .agentLoop()
        .run("Run a sequence of tasks", context)
        .onComplete(
            testContext.succeeding(
                result ->
                    testContext.verify(
                        () -> {
                          assertTrue(result.contains("successfully"));
                          testContext.completeNow();
                        })));
  }
}
