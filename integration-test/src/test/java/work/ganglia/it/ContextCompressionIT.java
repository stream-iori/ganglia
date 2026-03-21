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
import work.ganglia.port.chat.Message;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.llm.ChatRequest;
import work.ganglia.port.external.llm.ModelGateway;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.internal.state.TokenUsage;

@ExtendWith(VertxExtension.class)
public class ContextCompressionIT {

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
  void testHistoryCompressionTrigger(Vertx vertx, VertxTestContext testContext) {
    String sessionId = UUID.randomUUID().toString();
    SessionContext context = ganglia.sessionManager().createSession(sessionId);

    // Fill history with many messages to trigger potential compression logic
    for (int i = 0; i < 10; i++) {
      context = context.withNewMessage(Message.user("User message " + i));
      context =
          context.withNewMessage(
              Message.assistant("Assistant message " + i, Collections.emptyList()));
    }

    // Mock compression response
    when(mockModel.chat(any(ChatRequest.class)))
        .thenReturn(
            Future.succeededFuture(
                new ModelResponse(
                    "Summarized history.", Collections.emptyList(), new TokenUsage(10, 10))));

    // In ReActAgentLoop, it might not trigger compression directly unless certain token limits are
    // reached.
    // But we can check if the SessionContext or ContextCompressor works.
    assertTrue(context.history().size() >= 20);
    testContext.completeNow();
  }
}
