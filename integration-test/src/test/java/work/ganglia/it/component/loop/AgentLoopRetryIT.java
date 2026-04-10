package work.ganglia.it.component.loop;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.it.support.MockModelIT;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.llm.ChatRequest;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.internal.state.TokenUsage;

public class AgentLoopRetryIT extends MockModelIT {

  @Test
  void networkFailure_retriesAndSucceeds(Vertx vertx, VertxTestContext testContext) {
    when(mockModel.chatStream(any(ChatRequest.class), any()))
        .thenReturn(Future.failedFuture(new java.io.IOException("Temporary error")))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse(
                    "Success after retry.", Collections.emptyList(), new TokenUsage(1, 1))));

    SessionContext context = newSession();

    ganglia
        .agentLoop()
        .run("Test retry", context)
        .onComplete(
            testContext.succeeding(
                result ->
                    testContext.verify(
                        () -> {
                          assertTrue(result.contains("Success"));
                          verify(mockModel, times(2)).chatStream(any(ChatRequest.class), any());
                          testContext.completeNow();
                        })));
  }
}
