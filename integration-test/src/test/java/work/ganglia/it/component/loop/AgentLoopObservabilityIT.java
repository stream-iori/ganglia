package work.ganglia.it.component.loop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.it.support.MockModelIT;
import work.ganglia.kernel.loop.AgentLoopObserver;
import work.ganglia.kernel.loop.DefaultObservationDispatcher;
import work.ganglia.kernel.loop.ReActAgentLoop;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.llm.ChatRequest;
import work.ganglia.port.external.llm.LLMException;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.external.tool.ObservationType;
import work.ganglia.port.internal.state.TokenUsage;

public class AgentLoopObservabilityIT extends MockModelIT {

  @Test
  void modelCallObservations_dispatchedDuringWorkflow(Vertx vertx, VertxTestContext testContext) {
    AtomicReference<Map<String, Object>> startedData = new AtomicReference<>();
    AtomicReference<Map<String, Object>> finishedData = new AtomicReference<>();

    ReActAgentLoop loop = (ReActAgentLoop) ganglia.agentLoop();
    if (loop.getDispatcher() instanceof DefaultObservationDispatcher dod) {
      dod.register(
          new AgentLoopObserver() {
            @Override
            public void onObservation(
                String sessionId, ObservationType type, String content, Map<String, Object> data) {
              if (type == ObservationType.MODEL_CALL_STARTED) {
                startedData.set(data);
              } else if (type == ObservationType.MODEL_CALL_FINISHED) {
                finishedData.set(data);
              }
            }

            @Override
            public void onUsageRecorded(String sessionId, TokenUsage usage) {}
          });
    }

    when(mockModel.chatStream(any(ChatRequest.class), any()))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse("Done.", Collections.emptyList(), new TokenUsage(10, 5))));

    SessionContext context = newSession();

    ganglia
        .agentLoop()
        .run("Test model call observations", context)
        .onComplete(
            testContext.succeeding(
                result ->
                    testContext.verify(
                        () -> {
                          assertNotNull(startedData.get(), "MODEL_CALL_STARTED should be observed");
                          assertEquals(1, startedData.get().get("attempt"), "attempt should be 1");
                          assertTrue(
                              (Boolean) startedData.get().get("streaming"),
                              "streaming should be true");
                          assertNotNull(startedData.get().get("model"), "model should be present");

                          assertNotNull(
                              finishedData.get(), "MODEL_CALL_FINISHED should be observed");
                          assertEquals("success", finishedData.get().get("status"));
                          assertNotNull(
                              finishedData.get().get("durationMs"), "durationMs should be present");
                          testContext.completeNow();
                        })));
  }

  @Test
  void errorClassification_llmErrorIncludesTypeAndRecoverable(
      Vertx vertx, VertxTestContext testContext) {
    AtomicReference<Map<String, Object>> errorData = new AtomicReference<>();

    ReActAgentLoop loop = (ReActAgentLoop) ganglia.agentLoop();
    if (loop.getDispatcher() instanceof DefaultObservationDispatcher dod) {
      dod.register(
          new AgentLoopObserver() {
            @Override
            public void onObservation(
                String sessionId, ObservationType type, String content, Map<String, Object> data) {
              if (type == ObservationType.ERROR && data != null && data.containsKey("errorType")) {
                errorData.set(data);
              }
            }

            @Override
            public void onUsageRecorded(String sessionId, TokenUsage usage) {}
          });
    }

    when(mockModel.chatStream(any(ChatRequest.class), any()))
        .thenReturn(
            Future.failedFuture(
                new LLMException("Rate limited", "rate_limit_exceeded", 429, "req-123", null)));

    SessionContext context = newSession();

    ganglia
        .agentLoop()
        .run("Trigger LLM error", context)
        .onComplete(
            testContext.failing(
                err ->
                    testContext.verify(
                        () -> {
                          assertNotNull(errorData.get(), "ERROR observation should be captured");
                          assertEquals("llm_error", errorData.get().get("errorType"));
                          assertEquals(true, errorData.get().get("recoverable"));
                          assertEquals(429, errorData.get().get("httpStatusCode"));
                          assertEquals("rate_limit_exceeded", errorData.get().get("errorCode"));
                          testContext.completeNow();
                        })));
  }

  @Test
  void tokenUsageRecorded_dispatchedAfterModelResponse(Vertx vertx, VertxTestContext testContext) {
    List<Map<String, Object>> usageObservations = Collections.synchronizedList(new ArrayList<>());

    ReActAgentLoop loop = (ReActAgentLoop) ganglia.agentLoop();
    if (loop.getDispatcher() instanceof DefaultObservationDispatcher dod) {
      dod.register(
          new AgentLoopObserver() {
            @Override
            public void onObservation(
                String sessionId, ObservationType type, String content, Map<String, Object> data) {
              if (type == ObservationType.TOKEN_USAGE_RECORDED) {
                usageObservations.add(data);
              }
            }

            @Override
            public void onUsageRecorded(String sessionId, TokenUsage usage) {}
          });
    }

    when(mockModel.chatStream(any(ChatRequest.class), any()))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse("Done.", Collections.emptyList(), new TokenUsage(100, 50))));

    SessionContext context = newSession();

    ganglia
        .agentLoop()
        .run("Test token usage observation", context)
        .onComplete(
            testContext.succeeding(
                result ->
                    testContext.verify(
                        () -> {
                          assertEquals(
                              1, usageObservations.size(), "Should have 1 TOKEN_USAGE_RECORDED");
                          Map<String, Object> data = usageObservations.get(0);
                          assertEquals(100, data.get("promptTokens"));
                          assertEquals(50, data.get("completionTokens"));
                          assertEquals(150, data.get("totalTokens"));
                          testContext.completeNow();
                        })));
  }
}
