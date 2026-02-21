package me.stream.ganglia.core.state;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.OpenOptions;
import me.stream.ganglia.core.model.Message;
import me.stream.ganglia.core.model.SessionContext;
import me.stream.ganglia.core.model.Turn;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public class FileLogManager implements LogManager {
    private static final String LOG_DIR = ".ganglia/logs";
    private final Vertx vertx;

    public FileLogManager(Vertx vertx) {
        this.vertx = vertx;
    }

    @Override
    public Future<Void> appendLog(SessionContext context) {
        // Simple append strategy: Just log the latest event from the current turn
        // In a real system, we'd want to be smarter to avoid duplicates.
        // For now, let's assume we log the whole Turn if it's new? No.
        // Let's log the *last message* added.
        // SessionContext is immutable, so we can't easily know "what changed" without diffing.
        // But ReActAgentLoop calls this after adding a message.

        // Strategy: We won't implement full diffing here yet.
        // We'll just ensure the log file exists.
        // Actual logging logic might need to be triggered by ReActAgentLoop with the specific message.
        // But interface takes Context.

        // Let's log the current turn's latest step.
        Turn current = context.currentTurn();
        if (current == null) return Future.succeededFuture();

        Message latest = current.getLatestMessage();
        if (latest == null) return Future.succeededFuture();

        String logEntry = formatLogEntry(latest);
        String filename = getLogFileName();

        return vertx.fileSystem()
            .open(filename, new OpenOptions().setAppend(true).setCreate(true))
            .compose(asyncFile -> asyncFile.write(Buffer.buffer(logEntry))
                .compose(v -> asyncFile.close())
            );
    }

    private String formatLogEntry(Message msg) {
        return String.format("[%s] %s: %s\n", msg.timestamp(), msg.role(), msg.content());
    }

    private String getLogFileName() {
        return LOG_DIR + "/" + LocalDate.now().format(DateTimeFormatter.ISO_DATE) + ".md";
    }
}
