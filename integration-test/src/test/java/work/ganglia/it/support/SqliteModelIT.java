package work.ganglia.it.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.BootstrapOptions;
import work.ganglia.Ganglia;
import work.ganglia.coding.CodingAgentBuilder;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.llm.ChatRequest;
import work.ganglia.port.external.llm.ModelGateway;

/**
 * Base class for integration tests that bootstrap Ganglia with the SQLite memory backend. Mirrors
 * {@link MockModelIT} but configures {@code storageBackend=sqlite}.
 */
@ExtendWith(VertxExtension.class)
public abstract class SqliteModelIT {

  protected Ganglia ganglia;
  protected ModelGateway mockModel;

  @TempDir protected Path tempDir;

  @BeforeEach
  void bootstrapGanglia(Vertx vertx, VertxTestContext testContext) throws IOException {
    mockModel = mock(ModelGateway.class);
    when(mockModel.chat(any(ChatRequest.class)))
        .thenReturn(Future.failedFuture("Reflection disabled"));

    String projectRoot = tempDir.toRealPath().toString();

    BootstrapOptions options =
        BootstrapOptions.builder()
            .projectRoot(projectRoot)
            .modelGatewayOverride(mockModel)
            .overrideConfig(
                new JsonObject()
                    .put("webui", new JsonObject().put("enabled", false))
                    .put("agent", new JsonObject().put("storageBackend", "sqlite")))
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
