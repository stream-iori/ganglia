package work.ganglia.web;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import work.ganglia.web.model.EventType;
import work.ganglia.web.model.ServerEvent;
import work.ganglia.kernel.loop.AgentLoop;
import work.ganglia.port.internal.state.SessionManager;
import work.ganglia.util.Constants;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.mock;

@ExtendWith(VertxExtension.class)
public class WebUIProtocolTest {

    static Stream<Arguments> protocolActionProvider() {
        return Stream.of(
            // Action, Payload, Expected Event Type, Validation Key
            arguments("SYNC", new JsonObject(), EventType.INIT_CONFIG, "workspacePath"),
            arguments("READ_FILE", new JsonObject().put("path", "WORKSPACE_DIFF_VIRTUAL_PATH"), EventType.FILE_CONTENT, "content")
        );
    }

    @ParameterizedTest(name = "Should trigger {2} event when {0} is requested")
    @MethodSource("protocolActionProvider")
    @DisplayName("Parameterized Protocol Request Test")
    void shouldHandleProtocolActions(
            String action, 
            JsonObject payload, 
            EventType expectedEventType, 
            String validationKey,
            Vertx vertx, 
            VertxTestContext testContext) {
        
        String sessionId = "proto-session-" + action;
        WebUIVerticle verticle = new WebUIVerticle(0, mock(AgentLoop.class), mock(SessionManager.class));

        vertx.deployVerticle(verticle).onComplete(res -> {
            // Listen for the expected event
            vertx.eventBus().<JsonObject>consumer(Constants.ADDRESS_UI_STREAM_PREFIX + sessionId, message -> {
                testContext.verify(() -> {
                    ServerEvent event = message.body().mapTo(ServerEvent.class);
                    if (event.type() == expectedEventType) {
                        JsonObject data = JsonObject.mapFrom(event.data());
                        assertNotNull(data.getValue(validationKey), "Missing required data field: " + validationKey);
                        testContext.completeNow();
                    }
                });
            });

            // Send the request
            JsonObject request = new JsonObject()
                .put("action", action)
                .put("sessionId", sessionId)
                .put("payload", payload);
            
            vertx.eventBus().send(Constants.ADDRESS_UI_REQ, request);
        });
    }
}
