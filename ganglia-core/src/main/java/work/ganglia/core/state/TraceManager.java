package work.ganglia.core.state;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.json.JsonObject;
import work.ganglia.core.config.ConfigManager;
import work.ganglia.core.model.ObservationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Persists observation events into a structured trace.md file.
 * Supports date-based file rotation.
 */
public class TraceManager {
    private static final Logger logger = LoggerFactory.getLogger(TraceManager.class);
    private final Vertx vertx;
    private final ConfigManager configManager;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    public TraceManager(Vertx vertx, ConfigManager configManager) {
        this.vertx = vertx;
        this.configManager = configManager;

        // Listen to all observations
        vertx.eventBus().consumer("ganglia.observations.*", this::handleEvent);
    }

    private void handleEvent(Message<JsonObject> message) {
        if (!configManager.isObservabilityEnabled()) {
            return;
        }

        ObservationEvent event = message.body().mapTo(ObservationEvent.class);
        String markdown = formatEventToMarkdown(event);
        String filename = getTraceFileName();

        ensureDirExists(configManager.getTracePath());

        vertx.fileSystem().open(filename, new OpenOptions().setAppend(true).setCreate(true))
                .compose(asyncFile -> asyncFile.write(Buffer.buffer(markdown)).compose(v -> asyncFile.close()))
                .onFailure(err -> logger.error("Failed to write to trace file: {}", filename, err));
    }

    private String formatEventToMarkdown(ObservationEvent event) {
        StringBuilder sb = new StringBuilder();
        String time = LocalTime.now().format(TIME_FORMATTER);

        switch (event.type()) {
            case TURN_STARTED:
                sb.append("\n---\n# Turn Started [").append(time).append("] Session: ").append(event.sessionId()).append("\n");
                if (event.content() != null) sb.append("**User:** ").append(event.content()).append("\n");
                break;
            case REASONING_STARTED:
                sb.append("\n### \uD83D\uDCA1 Thought [").append(time).append("]\n");
                break;
            case REASONING_FINISHED:
                if (event.content() != null && !event.content().isEmpty()) {
                    sb.append(event.content()).append("\n");
                }
                sb.append("\n--- Reasoning Finished ---\n");
                break;
            case TOOL_STARTED:
                sb.append("\n### \uD83D\uDEE0\uFE0F Tool Call: `").append(event.content()).append("` [").append(time).append("]\n");
                if (event.data() != null && !event.data().isEmpty()) {
                    sb.append("- **Arguments:** `").append(JsonObject.mapFrom(event.data()).encode()).append("`\n");
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
                if (event.content() != null) sb.append("**Final Response:** ").append(event.content()).append("\n");
                break;
            case ERROR:
                sb.append("\n### \u274C Error [").append(time).append("]\n");
                sb.append("**Message:** ").append(event.content()).append("\n");
                if (event.data() != null && !event.data().isEmpty()) {
                    sb.append("**Details:**\n");
                    event.data().forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append("\n"));
                }
                break;
            case TOKEN_RECEIVED:
                sb.append(event.content());
                break;
        }

        return sb.toString();
    }

    private String getTraceFileName() {
        String date = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        return configManager.getTracePath() + "/trace-" + date + ".md";
    }

    private void ensureDirExists(String path) {
        File dir = new File(path);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }
}
