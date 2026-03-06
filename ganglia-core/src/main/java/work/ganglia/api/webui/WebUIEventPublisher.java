package work.ganglia.api.webui;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import work.ganglia.kernel.loop.AgentLoopObserver;
import work.ganglia.port.external.tool.ObservationType;
import work.ganglia.port.internal.state.TokenUsage;
import work.ganglia.util.Constants;
import work.ganglia.api.webui.model.EventType;
import work.ganglia.api.webui.model.ServerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;

/**
 * Publishes Agent Loop observations to the WebUI via EventBus.
 */
public class WebUIEventPublisher implements AgentLoopObserver {
    private static final Logger logger = LoggerFactory.getLogger(WebUIEventPublisher.class);
    private final Vertx vertx;

    public WebUIEventPublisher(Vertx vertx) {
        this.vertx = vertx;
    }

    private void publish(String sessionId, EventType type, Object data) {
        publish(sessionId, type, data, true);
    }

    private void publish(String sessionId, EventType type, Object data, boolean shouldCache) {
        vertx.runOnContext(v -> {
            ServerEvent event = new ServerEvent(
                UUID.randomUUID().toString(),
                System.currentTimeMillis(),
                type,
                data
            );
            JsonObject json = JsonObject.mapFrom(event);
            String address = Constants.ADDRESS_UI_STREAM_PREFIX + sessionId;
            logger.debug("Publishing WebUI event: {} to address: {}", type, address);
            vertx.eventBus().publish(address, json);
            
            if (shouldCache) {
                // Also send to internal cache topic
                vertx.eventBus().send(Constants.ADDRESS_UI_OUTBOUND_CACHE, json, 
                    new io.vertx.core.eventbus.DeliveryOptions().addHeader("sessionId", sessionId));
            }
        });
    }

    @Override
    public void onObservation(String sessionId, ObservationType type, String content, Map<String, Object> data) {
        switch (type) {
            case REASONING_STARTED -> {
                publish(sessionId, EventType.THOUGHT, new ServerEvent.ThoughtData("..."), false);
            }
            case REASONING_FINISHED -> {
                if (content != null && !content.isBlank()) {
                    publish(sessionId, EventType.THOUGHT, new ServerEvent.ThoughtData(content));
                }
            }
            case TOKEN_RECEIVED -> {
                if (content != null && !content.isEmpty()) {
                    // Do NOT cache individual tokens in session history to avoid bloating
                    publish(sessionId, EventType.TOKEN, new ServerEvent.TokenData(content), false);
                }
            }
            case TOOL_STARTED -> {
                String toolCallId = data != null && data.containsKey("toolCallId") ? data.get("toolCallId").toString() : UUID.randomUUID().toString();
                publish(sessionId, EventType.TOOL_START, new ServerEvent.ToolStartData(
                    toolCallId,
                    content,
                    content
                ));
            }
            case TOOL_FINISHED -> {
                String toolCallId = data != null && data.containsKey("toolCallId") ? data.get("toolCallId").toString() : "";
                publish(sessionId, EventType.TOOL_RESULT, new ServerEvent.ToolResultData(
                    toolCallId,
                    0,  // exitCode
                    "Executed: " + content,
                    content,
                    false,
                    null
                ));
            }
            case TURN_FINISHED -> {
                if (content != null && !content.isBlank()) {
                    publish(sessionId, EventType.AGENT_MESSAGE, new ServerEvent.AgentMessageData(content));
                }
            }
            case ERROR -> {
                String errorCode = data != null && data.containsKey("errorCode") ? data.get("errorCode").toString() : "LOOP_ERROR";
                boolean canRetry = data == null || !Boolean.FALSE.equals(data.get("canRetry")); // Default true
                
                publish(sessionId, EventType.SYSTEM_ERROR, new ServerEvent.SystemErrorData(
                    errorCode,
                    content,
                    data != null ? data.toString() : "",
                    canRetry
                ));
            }
        }
    }

    @Override
    public void onUsageRecorded(String sessionId, TokenUsage usage) {
        // Optional: send usage info to UI
    }
}
