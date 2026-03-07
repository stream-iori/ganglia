package work.ganglia.it;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import work.Main; 
import work.ganglia.Ganglia;
import work.ganglia.port.external.llm.ModelGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import work.ganglia.port.chat.*;
import work.ganglia.port.external.llm.*;
import work.ganglia.port.external.tool.*;
import work.ganglia.port.internal.state.*;;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(VertxExtension.class)
public class ContextCompressionIT {

    private Ganglia ganglia;
    private ModelGateway mockModel;
    private SessionContext baseContext;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext) {
        mockModel = mock(ModelGateway.class);

        // Mock Config to have a very small context limit
        io.vertx.core.json.JsonObject configOverride = new io.vertx.core.json.JsonObject()
            .put("agent", new io.vertx.core.json.JsonObject()
                .put("compressionThreshold", 0.5))
            .put("models", new io.vertx.core.json.JsonObject()
                .put("primary", new io.vertx.core.json.JsonObject()
                    .put("contextLimit", 100))); // 100 tokens limit

        Main.bootstrap(vertx, ".ganglia/config.json", configOverride.put("webui", new JsonObject().put("enabled", false)), mockModel)
            .onComplete(testContext.succeeding((Ganglia g) -> {
                this.ganglia = g;
                this.baseContext = ganglia.sessionManager().createSession(UUID.randomUUID().toString());
                testContext.completeNow();
            }));
    }

    @Test
    void testProactiveCompression(Vertx vertx, VertxTestContext testContext) {
        // 1. Create a history that exceeds 50 tokens (50% of 100)
        // Each turn usually adds some overhead. We'll add 3 turns.
        Turn t1 = Turn.newTurn("t1", Message.user("Short message 1"));
        Turn t2 = Turn.newTurn("t2", Message.user("Longer message to definitely trigger threshold " + "a".repeat(50)));

        baseContext = baseContext.withPreviousTurns(List.of(t1, t2));

        // 2. Mock Model Responses
        // One for compression, one for final answer
        when(mockModel.chat(any(), any(), any()))
            .thenReturn(Future.succeededFuture(new ModelResponse("Compressed Summary", Collections.emptyList(), new TokenUsage(10, 5))));

        when(mockModel.chatStream(any(), any(), any(), any()))
            .thenReturn(Future.succeededFuture(new ModelResponse("Final Answer", Collections.emptyList(), new TokenUsage(10, 10))));

        // 3. Run the loop
        ganglia.agentLoop().run("Trigger reason", baseContext)
            .onComplete(testContext.succeeding(result -> {
                testContext.verify(() -> {
                    // Check if compression was called (model.chat is used for utility/compression)
                    verify(mockModel, atLeastOnce()).chat(any(), any(), any());
                    testContext.completeNow();
                });
            }));
    }
}
