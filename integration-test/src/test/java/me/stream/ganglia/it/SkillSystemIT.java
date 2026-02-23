package me.stream.ganglia.it;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import me.stream.Main;
import me.stream.ganglia.core.Ganglia;
import me.stream.ganglia.core.llm.ModelGateway;
import me.stream.ganglia.core.model.*;
import me.stream.ganglia.tools.model.ToolCall;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(VertxExtension.class)
public class SkillSystemIT {

    private Ganglia ganglia;
    private ModelGateway mockModel;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext) {
        mockModel = mock(ModelGateway.class);
        when(mockModel.chat(any(), any(), any())).thenReturn(Future.failedFuture("Reflection disabled in tests"));
        Main.bootstrap(vertx, ".ganglia/config.json", null, mockModel)
            .onComplete(testContext.succeeding(g -> {
                this.ganglia = g;
                testContext.completeNow();
            }));
    }

    @Test
    void testSkillDiscoveryAndActivation(Vertx vertx, VertxTestContext testContext) {
        // 1. Prepare trigger file
        vertx.fileSystem().writeFileBlocking("it-test.it-test", Buffer.buffer("trigger"));

        // 2. Mock activation call
        ToolCall activateCall = new ToolCall("c1", "activate_skill", Map.of("skillId", "it-test-skill", "confirmed", true));
        
        when(mockModel.chatStream(any(), any(), any(), any()))
            .thenReturn(Future.succeededFuture(new ModelResponse("Activating skill.", List.of(activateCall), new TokenUsage(1, 1))))
            .thenReturn(Future.succeededFuture(new ModelResponse("Skill activated.", Collections.emptyList(), new TokenUsage(1, 1))));

        SessionContext context = ganglia.sessionManager().createSession(UUID.randomUUID().toString());

        ganglia.agentLoop().run("I see a .it-test file. Please activate the relevant skill.", context)
            .onComplete(testContext.succeeding(result -> {
                testContext.verify(() -> {
                    assertTrue(result.contains("activated"));
                    // Cleanup
                    vertx.fileSystem().delete("it-test.it-test").onComplete(ar -> testContext.completeNow());
                });
            }))
            .onFailure(testContext::failNow);
    }
}
