package work.ganglia.infrastructure.internal.state;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.json.JsonObject;

import work.ganglia.config.ObservabilityConfigProvider;
import work.ganglia.port.external.tool.ObservationEvent;
import work.ganglia.util.Constants;

/**
 * Persists observation events into a structured trace.md file. Supports date-based file rotation.
 */
public class TraceManager {
  private static final Logger logger = LoggerFactory.getLogger(TraceManager.class);
  private final Vertx vertx;
  private final ObservabilityConfigProvider configProvider;
  private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

  // Serialization chains to prevent concurrent write corruption
  private Future<Void> markdownWriteChain = Future.succeededFuture();
  private Future<Void> jsonlWriteChain = Future.succeededFuture();

  public TraceManager(Vertx vertx, ObservabilityConfigProvider configProvider) {
    this.vertx = vertx;
    this.configProvider = configProvider;

    // Listen to all observations
    vertx.eventBus().consumer(Constants.ADDRESS_OBSERVATIONS_ALL, this::handleEvent);
  }

  private void handleEvent(Message<JsonObject> message) {
    if (!configProvider.isObservabilityEnabled()) {
      return;
    }

    ObservationEvent event = message.body().mapTo(ObservationEvent.class);

    // Skip noisy token events in persistent traces to avoid corruption and reduce size
    if (event.type() == work.ganglia.port.external.tool.ObservationType.TOKEN_RECEIVED) {
      return;
    }

    String markdown = formatEventToMarkdown(event);
    String filename = getTraceFileName();
    String jsonlFilename = getJsonlFileName();
    String jsonl = message.body().encode() + "\n";

    ensureDirExists(configProvider.getTracePath());

    synchronized (this) {
      markdownWriteChain =
          markdownWriteChain
              .compose(
                  v ->
                      vertx
                          .fileSystem()
                          .open(filename, new OpenOptions().setAppend(true).setCreate(true))
                          .compose(
                              asyncFile ->
                                  asyncFile
                                      .write(Buffer.buffer(markdown))
                                      .compose(v2 -> asyncFile.close())))
              .onFailure(err -> logger.error("Failed to write to trace file: {}", filename, err))
              .recover(err -> Future.succeededFuture());

      jsonlWriteChain =
          jsonlWriteChain
              .compose(
                  v ->
                      vertx
                          .fileSystem()
                          .open(jsonlFilename, new OpenOptions().setAppend(true).setCreate(true))
                          .compose(
                              asyncFile ->
                                  asyncFile
                                      .write(Buffer.buffer(jsonl))
                                      .compose(v2 -> asyncFile.close())))
              .onFailure(
                  err ->
                      logger.error("Failed to write to jsonl trace file: {}", jsonlFilename, err))
              .recover(err -> Future.succeededFuture());
    }
  }

  private String formatEventToMarkdown(ObservationEvent event) {
    StringBuilder sb = new StringBuilder();
    String time = LocalTime.now().format(TIME_FORMATTER);

    switch (event.type()) {
      case SESSION_STARTED:
        sb.append("\n====\n# Session Started [")
            .append(time)
            .append("] Session: ")
            .append(event.sessionId())
            .append("\n");
        if (event.content() != null)
          sb.append("**Initial Prompt:** ").append(event.content()).append("\n");
        break;
      case TURN_STARTED:
        sb.append("\n---\n# Turn Started [")
            .append(time)
            .append("] Session: ")
            .append(event.sessionId())
            .append("\n");
        if (event.data() != null && event.data().containsKey("turnNumber"))
          sb.append("**Turn:** ").append(event.data().get("turnNumber")).append("\n");
        if (event.content() != null) sb.append("**User:** ").append(event.content()).append("\n");
        break;
      case REASONING_STARTED:
        sb.append("\n### \uD83D\uDCA1 Thought [").append(time).append("]\n");
        break;
      case REQUEST_PREPARED:
        if (event.data() != null) {
          sb.append("- **Request Prepared:** ");
          if (event.data().containsKey("messageCount"))
            sb.append(event.data().get("messageCount")).append(" messages, ");
          if (event.data().containsKey("toolCount"))
            sb.append(event.data().get("toolCount")).append(" tools, ");
          if (event.data().containsKey("model"))
            sb.append("model: `").append(event.data().get("model")).append("`");
          sb.append("\n");
        }
        break;
      case REASONING_FINISHED:
        if (event.content() != null && !event.content().isEmpty()) {
          sb.append(event.content()).append("\n");
        }
        sb.append("\n--- Reasoning Finished ---\n");
        break;
      case TOOL_STARTED:
        sb.append("\n### \uD83D\uDEE0\uFE0F Tool Call: `")
            .append(event.content())
            .append("` [")
            .append(time)
            .append("]\n");
        if (event.data() != null && !event.data().isEmpty()) {
          sb.append("- **Arguments:** `")
              .append(JsonObject.mapFrom(event.data()).encode())
              .append("`\n");
        }
        break;
      case TOOL_FINISHED:
        sb.append("- **Status:** \u2705 Success\n");
        if (event.content() != null && !event.content().isEmpty()) {
          sb.append("- **Output:** \n```\n").append(event.content()).append("\n```\n");
        }
        break;
      case TURN_FINISHED:
        sb.append("\n## Turn Finished [").append(time).append("]\n");
        if (event.data() != null && event.data().containsKey("turnNumber"))
          sb.append("**Turn:** ").append(event.data().get("turnNumber")).append("\n");
        if (event.content() != null)
          sb.append("**Final Response:** ").append(event.content()).append("\n");
        break;
      case SESSION_ENDED:
        sb.append("\n====\n# Session Ended [").append(time).append("]\n");
        if (event.data() != null && event.data().containsKey("durationMs")) {
          long ms = Long.parseLong(event.data().get("durationMs").toString());
          sb.append("**Duration:** ").append(ms / 1000).append("s (").append(ms).append("ms)\n");
        }
        break;
      case ERROR:
        sb.append("\n### \u274C Error [").append(time).append("]\n");
        sb.append("**Message:** ").append(event.content()).append("\n");
        if (event.data() != null && !event.data().isEmpty()) {
          sb.append("**Details:**\n");
          event
              .data()
              .forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append("\n"));
        }
        break;
      case TOKEN_RECEIVED:
        sb.append(event.content());
        break;
      case MODEL_CALL_STARTED:
        sb.append("- **API Call** [").append(time).append("]");
        if (event.data() != null) {
          if (event.data().containsKey("model"))
            sb.append(" model: `").append(event.data().get("model")).append("`");
          if (event.data().containsKey("attempt"))
            sb.append(" attempt: ").append(event.data().get("attempt"));
        }
        sb.append("\n");
        break;
      case MODEL_CALL_FINISHED:
        sb.append("- **API Call Finished** [").append(time).append("]");
        if (event.data() != null) {
          if (event.data().containsKey("status"))
            sb.append(" status: ").append(event.data().get("status"));
          if (event.data().containsKey("durationMs"))
            sb.append(" duration: ").append(event.data().get("durationMs")).append("ms");
          if (event.data().containsKey("error"))
            sb.append(" error: ").append(event.data().get("error"));
        }
        sb.append("\n");
        break;
      case TOKEN_USAGE_RECORDED:
        sb.append("- **Usage:**");
        if (event.data() != null) {
          sb.append(" prompt=").append(event.data().getOrDefault("promptTokens", 0));
          sb.append(" completion=").append(event.data().getOrDefault("completionTokens", 0));
          sb.append(" total=").append(event.data().getOrDefault("totalTokens", 0));
        }
        sb.append("\n");
        break;
      case MEMORY_UPDATED:
        sb.append("- _Memory updated:_ ").append(event.content() != null ? event.content() : "");
        sb.append("\n");
        break;
      default:
        break;
    }

    return sb.toString();
  }

  private String getTraceFileName() {
    String date = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
    return configProvider.getTracePath() + "/trace-" + date + ".md";
  }

  private String getJsonlFileName() {
    String date = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
    return configProvider.getTracePath() + "/trace-" + date + ".jsonl";
  }

  private void ensureDirExists(String path) {
    File dir = new File(path);
    if (!dir.exists()) {
      if (!dir.mkdirs()) {
        logger.warn("Failed to create directory: {}", path);
      }
    }
  }
}
