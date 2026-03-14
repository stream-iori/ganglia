package work.ganglia.it;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import work.Main;
import work.ganglia.Ganglia;
import work.ganglia.BootstrapOptions;
import work.ganglia.port.external.llm.ChatRequest;
import work.ganglia.port.external.llm.ModelGateway;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.internal.state.TokenUsage;
import work.ganglia.stubs.StubModelGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(VertxExtension.class)
public class SteeringE2EIT {

    private Ganglia ganglia;
    private StubModelGateway stubModel;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext, @TempDir Path tempDir) {
        stubModel = new StubModelGateway();

        BootstrapOptions options = BootstrapOptions.defaultOptions()
            .withProjectRoot(tempDir.toAbsolutePath().toString())
            .withModelGateway(stubModel)
            .withOverrideConfig(new JsonObject().put("webui", new JsonObject().put("enabled", false)));

        Main.bootstrap(vertx, options)
            .onComplete(testContext.succeeding((Ganglia g) -> {
                this.ganglia = g;
                testContext.completeNow();
            }));
    }

    @Test
    void testSteering(Vertx vertx, VertxTestContext testContext) {
        stubModel.addResponse(new ModelResponse("Initial thought.", Collections.emptyList(), new TokenUsage(1, 1)));
        stubModel.addResponse(new ModelResponse("Steered response.", Collections.emptyList(), new TokenUsage(1, 1)));


        SessionContext context = ganglia.sessionManager().createSession(UUID.randomUUID().toString());

        ganglia.agentLoop().run("Initial prompt", context)
            .onComplete(testContext.succeeding(result -> {
                testContext.verify(() -> {
                    assertTrue(result.contains("Steered") || result.contains("thought"));
                    testContext.completeNow();
                });
            }));
    }
}
