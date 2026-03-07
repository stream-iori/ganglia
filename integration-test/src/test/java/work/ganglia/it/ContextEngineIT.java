package work.ganglia.it;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import work.Main; 
import work.ganglia.Ganglia;
import work.ganglia.port.external.llm.ModelGateway;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.internal.state.TokenUsage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(VertxExtension.class)
public class ContextEngineIT {

    private Ganglia ganglia;
    private ModelGateway mockModel;
    private static final String GANGLIA_FILE = "GANGLIA.md";

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext) {
        mockModel = mock(ModelGateway.class);
        when(mockModel.chat(any(), any(), any(), any())).thenReturn(Future.failedFuture("Reflection disabled in tests"));
        Main.bootstrap(vertx, ".ganglia/config.json", new JsonObject().put("webui", new JsonObject().put("enabled", false)), mockModel)
            .onComplete(testContext.succeeding((Ganglia g) -> {
                this.ganglia = g;
                testContext.completeNow();
            }));
    }

    @AfterEach
    void tearDown(Vertx vertx, VertxTestContext testContext) {
        vertx.fileSystem().exists(GANGLIA_FILE).onComplete(ar -> {
            if (ar.succeeded() && ar.result()) {
                vertx.fileSystem().delete(GANGLIA_FILE).onComplete(v -> testContext.completeNow());
            } else {
                testContext.completeNow();
            }
        });
    }

    @Test
    void testAgentAdaptsToGangliaFile(Vertx vertx, VertxTestContext testContext) {
        String mandates = "## [Mandates]\n- You must always end your sentence with 'WOOF'.";

        vertx.fileSystem().writeFileBlocking(GANGLIA_FILE, Buffer.buffer(mandates));

        when(mockModel.chatStream(any(), any(), any(), any(), any()))
            .thenReturn(Future.succeededFuture(new ModelResponse("Understood WOOF", Collections.emptyList(), new TokenUsage(1, 1))));

        SessionContext context = ganglia.sessionManager().createSession(UUID.randomUUID().toString());

        ganglia.agentLoop().run("Hello", context)
            .onComplete(testContext.succeeding(result -> {
                testContext.verify(() -> {
                    assertTrue(result.contains("WOOF"));
                    testContext.completeNow();
                });
            }));
    }
}
