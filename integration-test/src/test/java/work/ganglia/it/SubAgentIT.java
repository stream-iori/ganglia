package work.ganglia.it;

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

import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.llm.ChatRequest;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.internal.state.TokenUsage;

public class SubAgentIT extends MockModelIT {

  @Test
  void delegation_executesAndReportsResult(Vertx vertx, VertxTestContext testContext) {
    ToolCall callSub = new ToolCall("c1", "call_sub_agent", Map.of("task", "SubTask"));

    when(mockModel.chatStream(any(ChatRequest.class), any()))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse("Delegating...", List.of(callSub), new TokenUsage(1, 1))))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse(
                    "SubAgent Result", Collections.emptyList(), new TokenUsage(1, 1))))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse("Final Answer", Collections.emptyList(), new TokenUsage(1, 1))));

    SessionContext context = newSession();

    ganglia
        .agentLoop()
        .run("Run subtask", context)
        .onComplete(
            testContext.succeeding(
                result ->
                    testContext.verify(
                        () -> {
                          assertTrue(result.contains("Final"));
                          testContext.completeNow();
                        })));
  }

  @Test
  void recursionProtection_rejectsAtMaxDepth(Vertx vertx, VertxTestContext testContext) {
    SessionContext context =
        new SessionContext(
            "test-session",
            Collections.emptyList(),
            null,
            Map.of("sub_agent_level", 3),
            null,
            null);

    ToolCall callSub = new ToolCall("c1", "call_sub_agent", Map.of("task", "SubTask"));

    when(mockModel.chatStream(any(ChatRequest.class), any()))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse("I will call subagent.", List.of(callSub), new TokenUsage(1, 1))))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse(
                    "Oops, recursion limit hit.", Collections.emptyList(), new TokenUsage(1, 1))));

    ganglia
        .agentLoop()
        .run("Trigger recursion", context)
        .onComplete(
            testContext.succeeding(
                result ->
                    testContext.verify(
                        () -> {
                          assertTrue(result.contains("recursion limit"));
                          testContext.completeNow();
                        })));
  }

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

  @Test
  void investigatorPersona_delegatesAndAggregates(Vertx vertx, VertxTestContext testContext) {
    ToolCall delegateCall =
        new ToolCall(
            "c1",
            "call_sub_agent",
            Map.of(
                "task", "Find three numbers in the project files.",
                "persona", "INVESTIGATOR"));

    String subAgentResult = "I found three numbers: 10, 20, and 30.";

    when(mockModel.chatStream(any(ChatRequest.class), any()))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse("Delegating...", List.of(delegateCall), new TokenUsage(10, 10))))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse(subAgentResult, Collections.emptyList(), new TokenUsage(5, 5))))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse(
                    "The sum of 10, 20, and 30 is 60.",
                    Collections.emptyList(),
                    new TokenUsage(10, 10))));

    SessionContext context = newSession();

    ganglia
        .agentLoop()
        .run("Find three numbers and sum them up", context)
        .onComplete(
            testContext.succeeding(
                result ->
                    testContext.verify(
                        () -> {
                          assertTrue(
                              result.contains("60"),
                              "Result should contain the sum 60. Got: " + result);
                          testContext.completeNow();
                        })));
  }
}
