package work.ganglia.it;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import work.ganglia.Ganglia;
import work.ganglia.BootstrapOptions;
import work.ganglia.coding.CodingAgentBuilder;
import work.ganglia.port.external.llm.ModelGateway;
import work.ganglia.port.chat.SessionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@ExtendWith(VertxExtension.class)
public class ContextEngineIT {

    private Ganglia ganglia;
    private ModelGateway mockModel;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext, @TempDir Path tempDir) throws java.io.IOException {
        mockModel = mock(ModelGateway.class);
        String projectRoot = tempDir.toRealPath().toString();

        BootstrapOptions options = BootstrapOptions.defaultOptions()
            .withProjectRoot(projectRoot)
            .withModelGateway(mockModel)
            .withOverrideConfig(new JsonObject().put("webui", new JsonObject().put("enabled", false)));

         CodingAgentBuilder.bootstrap(vertx, options)
            .onComplete(testContext.succeeding((Ganglia g) -> {
                this.ganglia = g;
                testContext.completeNow();
            }));
    }

    @Test
    void testPromptBuildingWithFullContext(Vertx vertx, VertxTestContext testContext) {
        SessionContext context = ganglia.sessionManager().createSession(UUID.randomUUID().toString());

        ganglia.env().promptEngine().buildSystemPrompt(context)
            .onComplete(testContext.succeeding((String prompt) -> {
                testContext.verify(() -> {
                    assertNotNull(prompt);
                    String upperPrompt = prompt.toUpperCase();
                    // Standard fragments should be present
                    assertTrue(upperPrompt.contains("PERSONA"), "Prompt should contain Persona");
                    assertTrue(upperPrompt.contains("OS"), "Prompt should contain OS information");
                    assertTrue(upperPrompt.contains("WORKING DIRECTORY"), "Prompt should contain Environment");
                    testContext.completeNow();
                });
            }));
    }

    @Test
    void testDynamicInstructionFileInPrompt(Vertx vertx, VertxTestContext testContext) {
        String customFile = "AGENTS.md";
        JsonObject configOverride = new JsonObject()
            .put("agent", new JsonObject().put("instructionFile", customFile));
        
        BootstrapOptions options = BootstrapOptions.defaultOptions()
            .withModelGateway(mockModel)
            .withOverrideConfig(configOverride);

        CodingAgentBuilder.bootstrap(vertx, options)
            .compose(g -> {
                SessionContext context = g.sessionManager().createSession(UUID.randomUUID().toString());
                return g.env().promptEngine().buildSystemPrompt(context);
            })
            .onComplete(testContext.succeeding(prompt -> {
                testContext.verify(() -> {
                    assertTrue(prompt.contains(customFile), "Prompt should contain the custom instruction filename: " + customFile);
                    testContext.completeNow();
                });
            }));
    }
}
