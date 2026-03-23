package work.ganglia.web;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.ganglia.kernel.loop.AgentLoopObserver;
import work.ganglia.port.external.tool.ObservationEvent;
import work.ganglia.port.external.tool.ObservationType;
import work.ganglia.port.internal.state.TokenUsage;
import work.ganglia.util.Constants;
import work.ganglia.web.model.EventType;
import work.ganglia.web.model.ServerEvent;
import work.ganglia.web.model.TtyEvent;

/** Publishes Agent Loop observations to the WebUI via EventBus. */
public class WebUIEventPublisher implements AgentLoopObserver {
  private static final Logger logger = LoggerFactory.getLogger(WebUIEventPublisher.class);
  private final Vertx vertx;

  public WebUIEventPublisher(Vertx vertx) {
    this.vertx = vertx;
    setupConsumer();
  }

  private void setupConsumer() {
    vertx
        .eventBus()
        .<JsonObject>consumer(
            Constants.ADDRESS_OBSERVATIONS_ALL,
            msg -> {
              try {
                ObservationEvent obs = msg.body().mapTo(ObservationEvent.class);
                onObservation(obs.sessionId(), obs.type(), obs.content(), obs.data());
              } catch (Exception e) {
                logger.error("Failed to process observation event from bus", e);
              }
            });
  }

  private void publish(String sessionId, EventType type, Object data) {
    publish(sessionId, type, data, true);
  }

  private void publish(String sessionId, EventType type, Object data, boolean shouldCache) {
    vertx.runOnContext(
        v -> {
          ServerEvent event =
              new ServerEvent(UUID.randomUUID().toString(), System.currentTimeMillis(), type, data);
          JsonObject json = JsonObject.mapFrom(event);
          String address = Constants.ADDRESS_UI_STREAM_PREFIX + sessionId;
          logger.debug("Publishing WebUI event: {} to address: {}", type, address);
          // Publish to old address for backward compatibility if needed, or just send to a central
          // router
          vertx
              .eventBus()
              .publish(
                  "ganglia.ui.ws.events",
                  json,
                  new io.vertx.core.eventbus.DeliveryOptions().addHeader("sessionId", sessionId));

          if (shouldCache) {
            // Also send to internal cache topic
            vertx
                .eventBus()
                .send(
                    Constants.ADDRESS_UI_OUTBOUND_CACHE,
                    json,
                    new io.vertx.core.eventbus.DeliveryOptions().addHeader("sessionId", sessionId));
          }
        });
  }

  @Override
  public void onObservation(
      String sessionId, ObservationType type, String content, Map<String, Object> data) {
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
        String toolCallId =
            data != null && data.containsKey("toolCallId")
                ? data.get("toolCallId").toString()
                : UUID.randomUUID().toString();
        String command =
            data != null && data.containsKey("command") ? data.get("command").toString() : content;
        publish(
            sessionId,
            EventType.TOOL_START,
            new ServerEvent.ToolStartData(
                toolCallId,
                content, // toolName
                command));
      }
      case TOOL_OUTPUT_STREAM -> {
        String toolCallId =
            data != null && data.containsKey("toolCallId") ? data.get("toolCallId").toString() : "";
        TtyEvent tty = new TtyEvent(toolCallId, content, false);
        vertx
            .eventBus()
            .publish(
                "ganglia.ui.ws.tty",
                JsonObject.mapFrom(tty),
                new io.vertx.core.eventbus.DeliveryOptions().addHeader("sessionId", sessionId));
      }
      case TOOL_FINISHED -> {
        String toolCallId =
            data != null && data.containsKey("toolCallId") ? data.get("toolCallId").toString() : "";
        int exitCode =
            data != null && data.containsKey("exitCode") ? (Integer) data.get("exitCode") : 0;
        boolean isError = data != null && Boolean.TRUE.equals(data.get("isError"));
        String summary =
            data != null && data.containsKey("summary")
                ? data.get("summary").toString()
                : "Executed: " + content;
        String fullOutput =
            data != null && data.containsKey("fullOutput")
                ? data.get("fullOutput").toString()
                : content;

        publish(
            sessionId,
            EventType.TOOL_RESULT,
            new ServerEvent.ToolResultData(
                toolCallId, exitCode, summary, fullOutput, isError, null));
      }
      case USER_INTERACTION_REQUIRED -> {
        String askId =
            data != null && data.containsKey("askId")
                ? data.get("askId").toString()
                : "ask-" + System.currentTimeMillis();

        java.util.List<ServerEvent.AskUserQuestion> questions = new java.util.ArrayList<>();

        if (data != null && data.containsKey("questions")) {
          Object qsObj = data.get("questions");
          java.util.List<?> rawQuestions;
          if (qsObj instanceof java.util.List) {
            rawQuestions = (java.util.List<?>) qsObj;
          } else if (qsObj instanceof io.vertx.core.json.JsonArray) {
            rawQuestions = ((io.vertx.core.json.JsonArray) qsObj).getList();
          } else {
            rawQuestions = java.util.Collections.emptyList();
          }

          for (Object qObj : rawQuestions) {
            if (qObj instanceof java.util.Map<?, ?> qMap) {
              Object questionVal = qMap.get("question");
              Object headerVal = qMap.get("header");
              Object typeVal = qMap.get("type");
              Object multiVal = qMap.get("multiSelect");
              Object placeholderVal = qMap.get("placeholder");

              String qText = questionVal != null ? questionVal.toString() : "";
              String header = headerVal != null ? headerVal.toString() : "";
              String qType = typeVal != null ? typeVal.toString() : "text";
              boolean multi = Boolean.TRUE.equals(multiVal);
              String placeholder = placeholderVal != null ? placeholderVal.toString() : "";

              java.util.List<ServerEvent.AskOption> options = new java.util.ArrayList<>();
              if (qMap.containsKey("options")) {
                Object optsObj = qMap.get("options");
                java.util.List<?> rawOpts;
                if (optsObj instanceof java.util.List) {
                  rawOpts = (java.util.List<?>) optsObj;
                } else if (optsObj instanceof io.vertx.core.json.JsonArray) {
                  rawOpts = ((io.vertx.core.json.JsonArray) optsObj).getList();
                } else {
                  rawOpts = java.util.Collections.emptyList();
                }

                for (Object optObj : rawOpts) {
                  if (optObj instanceof java.util.Map<?, ?> optMap) {
                    Object valObj = optMap.get("value");
                    Object labObj = optMap.get("label");
                    Object descObj = optMap.get("description");

                    String val = valObj != null ? valObj.toString() : "";
                    String lab = labObj != null ? labObj.toString() : val;
                    String desc = descObj != null ? descObj.toString() : "";
                    options.add(new ServerEvent.AskOption(val, lab, desc));
                  }
                }
              }
              questions.add(
                  new ServerEvent.AskUserQuestion(
                      qText, header, qType, options, multi, placeholder));
            }
          }
        }

        publish(sessionId, EventType.ASK_USER, new ServerEvent.AskUserData(askId, questions, null));
      }
      case TURN_FINISHED -> {
        if (content != null && !content.isBlank()) {
          publish(sessionId, EventType.AGENT_MESSAGE, new ServerEvent.AgentMessageData(content));
        }
      }
      case ERROR -> {
        String errorCode =
            data != null && data.containsKey("errorCode")
                ? data.get("errorCode").toString()
                : "LOOP_ERROR";
        boolean canRetry =
            data == null || !Boolean.FALSE.equals(data.get("canRetry")); // Default true

        publish(
            sessionId,
            EventType.SYSTEM_ERROR,
            new ServerEvent.SystemErrorData(
                errorCode, content, data != null ? data.toString() : "", canRetry));
      }
      case PLAN_UPDATED -> {
        if (data != null && data.containsKey("plan")) {
          work.ganglia.kernel.todo.ToDoList plan =
              (work.ganglia.kernel.todo.ToDoList) data.get("plan");
          publish(sessionId, EventType.PLAN_UPDATED, new ServerEvent.PlanUpdateData(plan));
        }
      }
    }
  }

  @Override
  public void onUsageRecorded(String sessionId, TokenUsage usage) {
    // Optional: send usage info to UI
  }
}
