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

public class SubAgentRecursionGuardIT extends MockModelIT {

  @Test
  void recursionProtection_rejectsAtMaxDepth(Vertx vertx, VertxTestContext testContext) {
    SessionContext context =
        new SessionContext(
            "test-session",
            Collections.emptyList(),
            null,
            Map.of("sub_agent_level", 3),
            null,
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
}
