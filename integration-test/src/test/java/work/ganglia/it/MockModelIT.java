package work.ganglia.it;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import work.ganglia.BootstrapOptions;
import work.ganglia.Ganglia;
import work.ganglia.coding.CodingAgentBuilder;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.llm.ChatRequest;
import work.ganglia.port.external.llm.ModelGateway;

/** Base class for integration tests that use a mock ModelGateway with standard bootstrap. */
@ExtendWith(VertxExtension.class)
public abstract class MockModelIT {

  protected Ganglia ganglia;
  protected ModelGateway mockModel;

  @TempDir protected Path tempDir;

  @BeforeEach
  void bootstrapGanglia(Vertx vertx, VertxTestContext testContext) throws java.io.IOException {
    mockModel = mock(ModelGateway.class);
    when(mockModel.chat(any(ChatRequest.class)))
        .thenReturn(Future.failedFuture("Reflection disabled"));

    String projectRoot = tempDir.toRealPath().toString();

    BootstrapOptions options =
        BootstrapOptions.builder()
            .projectRoot(projectRoot)
            .modelGatewayOverride(mockModel)
            .overrideConfig(new JsonObject().put("webui", new JsonObject().put("enabled", false)))
            .build();

    CodingAgentBuilder.bootstrap(vertx, options)
        .onComplete(
            testContext.succeeding(
                (Ganglia g) -> {
                  this.ganglia = g;
                  testContext.completeNow();
                }));
  }

  protected SessionContext newSession() {
    return ganglia.sessionManager().createSession(UUID.randomUUID().toString());
  }
}
