package work.ganglia.infrastructure.internal.memory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

import work.ganglia.port.internal.memory.TimelineLedger;
import work.ganglia.port.internal.memory.model.TimelineEvent;

public class MarkdownTimelineLedger implements TimelineLedger {
  private static final Logger log = LoggerFactory.getLogger(MarkdownTimelineLedger.class);
  private final Vertx vertx;
  private final Path timelineFile;
  private static final DateTimeFormatter formatter =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

  public MarkdownTimelineLedger(Vertx vertx, String basePath) {
    this.vertx = vertx;
    Path memoryDir = Paths.get(basePath).resolve(".ganglia/memory").toAbsolutePath().normalize();
    this.timelineFile = memoryDir.resolve("TIMELINE.md");

    if (!vertx.fileSystem().existsBlocking(memoryDir.toString())) {
      vertx.fileSystem().mkdirsBlocking(memoryDir.toString());
    }

    if (!vertx.fileSystem().existsBlocking(timelineFile.toString())) {
      String header = "# Ganglia System Timeline\n\nAutomated medical record of system events.\n\n";
      vertx
          .fileSystem()
          .writeFileBlocking(timelineFile.toString(), io.vertx.core.buffer.Buffer.buffer(header));
    }
  }

  @Override
  public Future<Void> appendEvent(TimelineEvent event) {
    try {
      String timestampStr = formatter.format(event.timestamp());
      StringBuilder markdown = new StringBuilder();
      markdown
          .append("### [")
          .append(timestampStr)
          .append("] ")
          .append(event.category())
          .append("\n");
      markdown.append("**ID:** `").append(event.eventId()).append("`\n\n");
      markdown.append(event.description()).append("\n\n");

      if (event.affectedFiles() != null && !event.affectedFiles().isEmpty()) {
        markdown.append("**Affected Files:**\n");
        for (String file : event.affectedFiles()) {
          markdown.append("- `").append(file).append("`\n");
        }
        markdown.append("\n");
      }
      markdown.append("---\n\n");

      // Append using file system
      io.vertx.core.file.OpenOptions options =
          new io.vertx.core.file.OpenOptions().setAppend(true).setCreate(true);
      return vertx
          .fileSystem()
          .open(timelineFile.toString(), options)
          .compose(
              file ->
                  file.write(io.vertx.core.buffer.Buffer.buffer(markdown.toString()))
                      .compose(v -> file.close()))
          .onSuccess(v -> log.debug("Appended event {} to timeline", event.eventId()));
    } catch (Exception e) {
      return Future.failedFuture(e);
    }
  }

  @Override
  public Future<List<TimelineEvent>> getRecentEvents(int limit) {
    // As a simple markdown ledger, parsing events from it for 'getRecentEvents' is complex.
    // For the scope of this implementation, we will return empty or unsupported,
    // as the primary use case is write-only automated record keeping.
    // If query is needed, an in-memory or structured backing store should be added.
    return Future.succeededFuture(Collections.emptyList());
  }
}
