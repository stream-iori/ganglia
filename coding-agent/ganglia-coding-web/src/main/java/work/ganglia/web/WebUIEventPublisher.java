package work.ganglia.web;

import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

import work.ganglia.port.external.tool.ObservationType;
import work.ganglia.port.internal.state.TokenUsage;
import work.ganglia.trajectory.event.BaseEventType;
import work.ganglia.trajectory.event.CommonEventData;
import work.ganglia.trajectory.publish.AbstractEventPublisher;
import work.ganglia.util.Constants;
import work.ganglia.web.model.CodingEventType;
import work.ganglia.web.model.ServerEvent;

/** Publishes Agent Loop observations to the WebUI via EventBus. */
public class WebUIEventPublisher extends AbstractEventPublisher {
  private static final Logger logger = LoggerFactory.getLogger(WebUIEventPublisher.class);

  public WebUIEventPublisher(Vertx vertx) {
    super(vertx);
  }

  @Override
  protected String eventsAddress() {
    return "ganglia.ui.ws.events";
  }

  @Override
  protected String cacheAddress() {
    return Constants.ADDRESS_UI_OUTBOUND_CACHE;
  }

  @Override
  public void onObservation(
      String sessionId,
      ObservationType type,
      String content,
      Map<String, Object> data,
      String spanId,
      String parentSpanId) {
    switch (type) {
      case TURN_STARTED -> {
        publish(
            sessionId, BaseEventType.USER_MESSAGE, new CommonEventData.UserMessageData(content));
      }
      case REASONING_STARTED -> {
        publish(sessionId, BaseEventType.THOUGHT, new CommonEventData.ThoughtData("..."), false);
      }
      case REASONING_FINISHED -> {
        if (content != null && !content.isBlank()) {
          publish(sessionId, BaseEventType.THOUGHT, new CommonEventData.ThoughtData(content));
        }
      }
      case TOKEN_RECEIVED -> {
        if (content != null && !content.isEmpty()) {
          publish(sessionId, BaseEventType.TOKEN, new CommonEventData.TokenData(content), false);
        }
      }
      case TOOL_STARTED -> {
        String toolCallId = extractString(data, "toolCallId", UUID.randomUUID().toString());
        String command = extractString(data, "command", content);
        publish(
            sessionId,
            BaseEventType.TOOL_START,
            new CommonEventData.ToolStartData(toolCallId, content, command));
      }
      case TOOL_OUTPUT_STREAM -> {
        String toolCallId = extractString(data, "toolCallId", "");
        var tty = new ServerEvent.ToolOutputStreamData(toolCallId, content, false);
        vertx
            .eventBus()
            .publish(
                "ganglia.ui.ws.tty",
                JsonObject.mapFrom(tty),
                new io.vertx.core.eventbus.DeliveryOptions().addHeader("sessionId", sessionId));
      }
      case TOOL_FINISHED -> {
        String toolCallId = extractString(data, "toolCallId", "");
        int exitCode = extractInt(data, "exitCode", 0);
        boolean isError = extractBoolean(data, "isError", false);
        String summary = extractString(data, "summary", "Executed: " + content);
        String fullOutput = extractString(data, "fullOutput", content);

        publish(
            sessionId,
            BaseEventType.TOOL_RESULT,
            new CommonEventData.ToolResultData(toolCallId, exitCode, summary, fullOutput, isError));
      }
      case USER_INTERACTION_REQUIRED -> {
        String askId = extractString(data, "askId", "ask-" + System.currentTimeMillis());

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

        String diffContext = data != null ? (String) data.get("diffContext") : null;
        publish(
            sessionId,
            CodingEventType.ASK_USER,
            new ServerEvent.AskUserData(askId, questions, diffContext));
      }
      case TURN_FINISHED -> {
        if (content != null && !content.isBlank()) {
          publish(
              sessionId,
              BaseEventType.AGENT_MESSAGE,
              new CommonEventData.AgentMessageData(content));
        }
      }
      case ERROR -> {
        String errorCode = extractString(data, "errorCode", "LOOP_ERROR");
        boolean canRetry = extractBoolean(data, "canRetry", true);

        publish(
            sessionId,
            BaseEventType.SYSTEM_ERROR,
            new CommonEventData.SystemErrorData(
                errorCode, content, data != null ? data.toString() : "", canRetry));
      }
      case PLAN_UPDATED -> {
        if (data != null && data.containsKey("plan")) {
          work.ganglia.kernel.todo.ToDoList plan =
              (work.ganglia.kernel.todo.ToDoList) data.get("plan");
          publish(sessionId, CodingEventType.PLAN_UPDATED, new ServerEvent.PlanUpdateData(plan));
        }
      }
      case SESSION_STARTED -> {
        String firstPrompt = extractString(data, "firstPrompt", content);
        publish(
            sessionId,
            BaseEventType.SESSION_STARTED,
            new CommonEventData.SessionStartedData(sessionId, firstPrompt));
      }
      case SESSION_ENDED -> {
        long durationMs = extractLong(data, "durationMs", 0L);
        publish(
            sessionId,
            BaseEventType.SESSION_ENDED,
            new CommonEventData.SessionEndedData(sessionId, durationMs),
            false);
      }
      default -> {
        /* Ignore other observation types */
      }
    }
  }

  @Override
  public void onUsageRecorded(String sessionId, TokenUsage usage) {
    // Optional: send usage info to UI
  }
}
