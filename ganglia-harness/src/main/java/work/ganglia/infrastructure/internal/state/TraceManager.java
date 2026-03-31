package work.ganglia.infrastructure.internal.state;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.json.JsonObject;

import work.ganglia.config.ObservabilityConfigProvider;
import work.ganglia.port.external.tool.ObservationType;
import work.ganglia.util.Constants;

/**
 * Persists observation events as JSONL (one JSON object per line) with date-based file rotation.
 */
public class TraceManager {
  private static final Logger logger = LoggerFactory.getLogger(TraceManager.class);
  private static final OpenOptions APPEND_OPTIONS =
      new OpenOptions().setAppend(true).setCreate(true);

  private final Vertx vertx;
  private final ObservabilityConfigProvider configProvider;
  private final MessageConsumer<JsonObject> consumer;

  /** Serialization chain to prevent concurrent write corruption. */
  private Future<Void> writeChain = Future.succeededFuture();

  private boolean dirEnsured = false;

  /** Cached file handle for the current date's trace file. */
  private AsyncFile currentFile;

  private String currentDate;

  public TraceManager(Vertx vertx, ObservabilityConfigProvider configProvider) {
    this.vertx = vertx;
    this.configProvider = configProvider;

    vertx
        .fileSystem()
        .mkdirs(configProvider.getTracePath())
        .onSuccess(v -> dirEnsured = true)
        .onFailure(err -> logger.warn("Failed to create trace directory: {}", err.getMessage()));

    this.consumer =
        vertx.eventBus().consumer(Constants.ADDRESS_OBSERVATIONS_ALL, this::handleEvent);
  }

  private void handleEvent(Message<JsonObject> message) {
    if (!configProvider.isObservabilityEnabled()) {
      return;
    }

    JsonObject body = message.body();
    String type = body.getString("type");

    // Skip noisy token events to reduce size
    if (ObservationType.TOKEN_RECEIVED.name().equals(type)) {
      return;
    }

    String line = body.encode() + "\n";
    String today = LocalDate.now().format(DateTimeFormatter.ISO_DATE);

    writeChain =
        writeChain
            .compose(v -> ensureDirAsync())
            .compose(v -> ensureFile(today))
            .compose(file -> file.write(Buffer.buffer(line)))
            .onFailure(err -> logger.error("Failed to write trace event", err))
            .recover(err -> Future.succeededFuture());
  }

  /** Opens a new file or reuses the cached one if the date hasn't changed. */
  private Future<AsyncFile> ensureFile(String date) {
    if (currentFile != null && date.equals(currentDate)) {
      return Future.succeededFuture(currentFile);
    }
    // Date changed or first call — close old file and open new one
    Future<Void> closePrev = currentFile != null ? currentFile.close() : Future.succeededFuture();
    return closePrev.compose(
        v -> {
          String filename = configProvider.getTracePath() + "/trace-" + date + ".jsonl";
          return vertx
              .fileSystem()
              .open(filename, APPEND_OPTIONS)
              .onSuccess(
                  file -> {
                    currentFile = file;
                    currentDate = date;
                  });
        });
  }

  private Future<Void> ensureDirAsync() {
    if (dirEnsured) {
      return Future.succeededFuture();
    }
    return vertx
        .fileSystem()
        .mkdirs(configProvider.getTracePath())
        .onSuccess(v -> dirEnsured = true);
  }

  /** Unregisters the event bus consumer and closes the cached file handle. */
  public Future<Void> close() {
    return consumer
        .unregister()
        .compose(
            v -> {
              if (currentFile != null) {
                return currentFile.close().onComplete(r -> currentFile = null);
              }
              return Future.succeededFuture();
            });
  }
}
