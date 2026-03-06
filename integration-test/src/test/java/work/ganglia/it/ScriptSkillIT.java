package work.ganglia.it;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import work.Main;
import work.ganglia.core.Ganglia;
import work.ganglia.core.llm.ModelGateway;
import work.ganglia.core.model.ModelResponse;
import work.ganglia.core.model.SessionContext;
import work.ganglia.core.model.TokenUsage;
import work.ganglia.tools.model.ToolCall;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(VertxExtension.class)
public class ScriptSkillIT {

    private Ganglia ganglia;
    private ModelGateway mockModel;
    private Path testProjectDir;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext) throws Exception {
        mockModel = mock(ModelGateway.class);

        // Create the skill directory in the CURRENT working directory because Main.bootstrap looks there
        testProjectDir = Path.of(".ganglia/skills/echo-skill");
        Files.createDirectories(testProjectDir);
        Files.writeString(testProjectDir.resolve("SKILL.md"), """
                ---
                id: echo-skill
                name: Echo Skill
                tools:
                  - name: script_echo
                    command: "echo 'SCRIPT_OUT: ${message}'"
                    schema: '{"type":"object","properties":{"message":{"type":"string"}}}'
                ---
                """);

        Main.bootstrap(vertx, null, null, mockModel)
            .onComplete(testContext.succeeding(g -> {
                this.ganglia = g;
                testContext.completeNow();
            }));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (testProjectDir != null) {
            Files.deleteIfExists(testProjectDir.resolve("SKILL.md"));
            Files.deleteIfExists(testProjectDir);
        }
    }

    @Test
    void testScriptToolExecution(Vertx vertx, VertxTestContext testContext) throws Exception {
        // 1. Activate the skill
        ToolCall activateCall = new ToolCall("c1", "activate_skill", Map.of("skillId", "echo-skill", "confirmed", true));

        // 2. Use the script tool
        ToolCall echoCall = new ToolCall("c2", "script_echo", Map.of("message", "hello ganglia"));

        when(mockModel.chatStream(any(), any(), any(), any()))
            .thenReturn(Future.succeededFuture(new ModelResponse("Activating...", List.of(activateCall), new TokenUsage(1, 1))))
            .thenReturn(Future.succeededFuture(new ModelResponse("Calling echo...", List.of(echoCall), new TokenUsage(1, 1))))
            .thenReturn(Future.succeededFuture(new ModelResponse("Done.", Collections.emptyList(), new TokenUsage(1, 1))));

        SessionContext context = ganglia.sessionManager().createSession(UUID.randomUUID().toString());

        ganglia.agentLoop().run("Use echo skill to say hello", context)
            .onComplete(testContext.succeeding(result -> {
                testContext.verify(() -> {
                    ganglia.sessionManager().getSession(context.sessionId())
                        .onComplete(testContext.succeeding(updatedContext -> {
                            testContext.verify(() -> {
                                boolean foundOutput = updatedContext.history().stream()
                                    .anyMatch(m -> m.content() != null && m.content().contains("SCRIPT_OUT: hello ganglia"));
                                assertTrue(foundOutput, "Should have found script output in history");
                                testContext.completeNow();
                            });
                        }));
                });
            }));
    }
}
