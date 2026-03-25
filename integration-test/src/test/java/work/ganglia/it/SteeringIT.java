package work.ganglia.it;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.llm.ChatRequest;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.internal.state.TokenUsage;

public class SteeringIT extends MockModelIT {

  @Test
  void steeringMessage_influencesNextResponse(Vertx vertx, VertxTestContext testContext) {
    String sessionId = UUID.randomUUID().toString();

    when(mockModel.chatStream(any(ChatRequest.class), any()))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse("Initial plan.", Collections.emptyList(), new TokenUsage(1, 1))))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse(
                    "Steered response.", Collections.emptyList(), new TokenUsage(1, 1))));

    SessionContext context = ganglia.sessionManager().createSession(sessionId);

    ganglia
        .agentLoop()
        .run("Initial prompt", context)
        .compose(
            res -> {
              ganglia
                  .sessionManager()
                  .addSteeringMessage(sessionId, "Actually, do something else.");
              return ganglia.agentLoop().run("Continue", context);
            })
        .onComplete(
            testContext.succeeding(
                result ->
                    testContext.verify(
                        () -> {
                          assertTrue(result.contains("Steered"));
                          testContext.completeNow();
                        })));
  }
}
