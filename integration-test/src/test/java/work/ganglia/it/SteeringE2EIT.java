package work.ganglia.it;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.nio.file.Path;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import work.ganglia.BootstrapOptions;
import work.ganglia.Ganglia;
import work.ganglia.coding.CodingAgentBuilder;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.llm.ChatRequest;
import work.ganglia.port.external.llm.ModelGateway;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.internal.state.TokenUsage;

@ExtendWith(VertxExtension.class)
public class SteeringE2EIT {

  private Ganglia ganglia;
  private ModelGateway mockModel;

  @BeforeEach
  void setUp(Vertx vertx, VertxTestContext testContext, @TempDir Path tempDir)
      throws java.io.IOException {
    mockModel = mock(ModelGateway.class);
    // Default failure for bootstrap
    when(mockModel.chat(any(ChatRequest.class)))
        .thenReturn(Future.failedFuture("Reflection disabled"));

    String projectRoot = tempDir.toRealPath().toString();

    BootstrapOptions options =
        BootstrapOptions.defaultOptions()
            .withProjectRoot(projectRoot)
            .withModelGateway(mockModel)
            .withOverrideConfig(
                new JsonObject().put("webui", new JsonObject().put("enabled", false)));

    CodingAgentBuilder.bootstrap(vertx, options)
        .onComplete(
            testContext.succeeding(
                (Ganglia g) -> {
                  this.ganglia = g;
                  testContext.completeNow();
                }));
  }

  @Test
  void testSteeringInfluence(Vertx vertx, VertxTestContext testContext) {
    String sessionId = UUID.randomUUID().toString();

    // Mock Model Response: initially ignore, then accept steering
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
                result -> {
                  testContext.verify(
                      () -> {
                        assertTrue(result.contains("Steered"));
                        testContext.completeNow();
                      });
                }));
  }
}
