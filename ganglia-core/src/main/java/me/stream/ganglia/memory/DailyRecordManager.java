package me.stream.ganglia.memory;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Manages daily record persistence in Markdown format.
 */
public class DailyRecordManager {
    private static final Logger logger = LoggerFactory.getLogger(DailyRecordManager.class);
    private final Vertx vertx;
    private final String basePath;

    public DailyRecordManager(Vertx vertx, String basePath) {
        this.vertx = vertx;
        this.basePath = basePath;
    }

    public Future<Void> record(String sessionId, String goal, String accomplishments) {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String fileName = "daily-" + dateStr + ".md";
        String filePath = basePath + "/" + fileName;

        String entry = "\n## [Session: " + sessionId + "]\n" +
                       "- **Goal:** " + goal + "\n" +
                       "- **Accomplishments:**\n" + accomplishments + "\n";

        return vertx.fileSystem().exists(filePath)
            .compose(exists -> {
                if (exists) {
                    return vertx.fileSystem().readFile(filePath)
                        .compose(existing -> vertx.fileSystem().writeFile(filePath, existing.appendBuffer(Buffer.buffer(entry))));
                } else {
                    String header = "# Daily Record: " + dateStr + "\n";
                    return vertx.fileSystem().writeFile(filePath, Buffer.buffer(header + entry));
                }
            })
            .onSuccess(v -> logger.debug("Daily record updated for session: {}", sessionId))
            .onFailure(err -> logger.error("Failed to update daily record", err));
    }
}
