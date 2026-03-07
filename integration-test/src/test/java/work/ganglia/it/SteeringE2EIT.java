package work.ganglia.it;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import work.Main; 
import work.ganglia.port.chat.Message;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.stubs.StubModelGateway;
import work.ganglia.port.external.tool.ToolCall;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(VertxExtension.class)
public class SteeringE2EIT {

    private Vertx vertx;
    private StubModelGateway stubModel;

    @BeforeEach
    void setUp(VertxTestContext testContext) {
        vertx = Vertx.vertx();
        stubModel = new StubModelGateway();

        // Ensure config exists
        vertx.fileSystem().writeFile(".ganglia/config.json", io.vertx.core.buffer.Buffer.buffer(new JsonObject()
                .put("agent", new JsonObject().put("maxIterations", 5))
                .put("models", new JsonObject()
                    .put("primary", new JsonObject().put("type", "stub"))
                    .put("utility", new JsonObject().put("type", "stub")))
                .put("observability", new JsonObject().put("enabled", false))
                .encodePrettily()))
            .onComplete(ar -> testContext.completeNow());
    }

    @Test
    void testSteeringMessageInterruptsToolExecution(VertxTestContext testContext) {
        Main.bootstrap(vertx, ".ganglia/config.json", new JsonObject().put("webui", new JsonObject().put("enabled", false)), stubModel).onComplete(testContext.succeeding(ganglia -> {

            // 1. First interaction: User asks to list and read two files.
            // Model responds with two tool calls.
            ToolCall call1 = new ToolCall("call_1", "run_shell_command", Map.of("command", "sleep 1")); // Simulate slow task
            ToolCall call2 = new ToolCall("call_2", "run_shell_command", Map.of("command", "echo 'should not run'"));
            ModelResponse response1 = new ModelResponse("I will execute two commands.", List.of(call1, call2), null);

            // 2. Second interaction: Model sees the steering message and stops.
            ModelResponse response2 = new ModelResponse("Understood, I will stop and not execute the second command.", List.of(), null);

            stubModel.addResponses(response1, response2);

            String sessionId = "steering-test-" + UUID.randomUUID().toString();
            SessionContext context = ganglia.sessionManager().createSession(sessionId);

            // 3. Start the loop in the background
            Future<String> loopFuture = ganglia.agentLoop().run("Execute my long task", context);

            // 4. Inject a steering message shortly after starting
            vertx.setTimer(100, id -> {
                ganglia.sessionManager().addSteeringMessage(sessionId, "STOP! Do not run the second command.");
            });

            loopFuture.onComplete(testContext.succeeding(finalAnswer -> {
                testContext.verify(() -> {
                    // 5. Verify the loop received the steering message and responded appropriately
                    assertTrue(finalAnswer.contains("Understood, I will stop"), "Final answer should reflect the steering instruction.");

                    // Verify the history contains the steering message
                    ganglia.sessionManager().getSession(sessionId).onComplete(testContext.succeeding(finalContext -> {
                        boolean foundSteering = false;
                        for (Message msg : finalContext.history()) {
                            if (msg.content().contains("System Interruption/Correction: STOP! Do not run the second command.")) {
                                foundSteering = true;
                                break;
                            }
                        }
                        assertTrue(foundSteering, "History should contain the injected steering message.");
                        testContext.completeNow();
                    }));
                });
            }));
        }));
    }
}
