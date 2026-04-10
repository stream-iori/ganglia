package work.ganglia.it.component.loop;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.it.support.MockModelIT;
import work.ganglia.kernel.loop.AgentAbortedException;
import work.ganglia.kernel.loop.AgentLoopObserver;
import work.ganglia.kernel.loop.DefaultObservationDispatcher;
import work.ganglia.kernel.loop.ReActAgentLoop;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.llm.ChatRequest;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.external.tool.ObservationType;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.internal.state.AgentSignal;
import work.ganglia.port.internal.state.TokenUsage;

public class AgentLoopAbortIT extends MockModelIT {

  @Test
  void abortSignal_doesNotPublishErrorObservation(Vertx vertx, VertxTestContext testContext) {
    SessionContext context = newSession();
    AgentSignal signal = new AgentSignal();

    AtomicBoolean errorObserved = new AtomicBoolean(false);
    ReActAgentLoop loop = (ReActAgentLoop) ganglia.agentLoop();
    if (loop.getDispatcher() instanceof DefaultObservationDispatcher dod) {
      dod.register(
          new AgentLoopObserver() {
            @Override
            public void onObservation(
                String sessionId, ObservationType type, String content, Map<String, Object> data) {
              if (type == ObservationType.ERROR) {
                errorObserved.set(true);
              }
            }

            @Override
            public void onUsageRecorded(
                String sessionId, work.ganglia.port.internal.state.TokenUsage usage) {}
          });
    }

    signal.abort();
    ganglia
        .agentLoop()
        .run("Abort me", context, signal)
        .onComplete(
            testContext.failing(
                err ->
                    testContext.verify(
                        () -> {
                          assertTrue(err instanceof AgentAbortedException);
                          assertTrue(
                              !errorObserved.get(),
                              "Should NOT publish ERROR observation for AgentAbortedException IT");
                          testContext.completeNow();
                        })));
  }

  @Test
  void consecutiveToolFailures_abortsWithPolicyError(Vertx vertx, VertxTestContext testContext) {
    ToolCall read1 = new ToolCall("c1", "read_file", Map.of("path", "no_such_file_1.txt"));
    ToolCall read2 = new ToolCall("c2", "read_file", Map.of("path", "no_such_file_2.txt"));
    ToolCall read3 = new ToolCall("c3", "read_file", Map.of("path", "no_such_file_3.txt"));

    when(mockModel.chatStream(any(ChatRequest.class), any()))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse("Reading file 1.", List.of(read1), new TokenUsage(1, 1))))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse("Reading file 2.", List.of(read2), new TokenUsage(1, 1))))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse("Reading file 3.", List.of(read3), new TokenUsage(1, 1))));

    SessionContext context = newSession();

    ganglia
        .agentLoop()
        .run("Read three missing files", context)
        .onComplete(
            testContext.failing(
                err ->
                    testContext.verify(
                        () -> {
                          assertTrue(err.getMessage().contains("repetitive task failures"));
                          testContext.completeNow();
                        })));
  }
}
