package work.ganglia.memory;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Manages daily record persistence in Markdown format on the file system.
 */
public class FileSystemDailyRecordManager implements DailyRecordManager {
    private static final Logger logger = LoggerFactory.getLogger(FileSystemDailyRecordManager.class);
    private final Vertx vertx;
    private final String basePath;

    public FileSystemDailyRecordManager(Vertx vertx, String basePath) {
        this.vertx = vertx;
        this.basePath = basePath;
    }

    @Override
    public Future<Void> record(String sessionId, String goal, String accomplishments) {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String fileName = "daily-" + dateStr + ".md";
        String filePath = basePath + "/" + fileName;

        String entry = "\n## [Session: " + sessionId + "]\n" +
                       "- **Goal:** " + goal + "\n" +
                       "- **Accomplishments:**\n" + accomplishments + "\n";

        return vertx.fileSystem().mkdirs(basePath)
            .compose(v -> vertx.fileSystem().exists(filePath))
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
            .onFailure(err -> {
                if (err instanceof java.util.concurrent.RejectedExecutionException) {
                    logger.debug("Daily record update aborted due to shutdown: {}", sessionId);
                } else {
                    logger.error("Failed to update daily record", err);
                }
            });
    }
}
