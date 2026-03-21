package work.ganglia.infrastructure.mcp.transport;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.streams.ReadStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StdioMcpTransport implements McpTransport, ReadStream<JsonObject> {
  private static final Logger log = LoggerFactory.getLogger(StdioMcpTransport.class);

  private final Vertx vertx;
  private final List<String> command;
  private final Map<String, String> environment;
  private Process process;
  private BufferedWriter writer;
  private Thread readThread;
  private Thread errorReadThread;

  private Handler<JsonObject> dataHandler;
  private Handler<Throwable> exceptionHandler;
  private Handler<Void> endHandler;

  private final AtomicBoolean paused = new AtomicBoolean(false);
  private final Context context;

  public StdioMcpTransport(Vertx vertx, List<String> command, Map<String, String> environment) {
    this.vertx = vertx;
    this.command = command;
    this.environment = environment;
    this.context = vertx.getOrCreateContext();
  }

  @Override
  public Future<Void> connect() {
    return vertx.executeBlocking(
        () -> {
          ProcessBuilder pb = new ProcessBuilder(command);
          if (environment != null) {
            pb.environment().putAll(environment);
          }
          process = pb.start();
          writer =
              new BufferedWriter(
                  new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));

          startReading();
          return null;
        });
  }

  private void startReading() {
    readThread =
        new Thread(
            () -> {
              try (BufferedReader reader =
                  new BufferedReader(
                      new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                  if (paused.get()) {
                    // In a real robust implementation, we'd queue or block.
                    // For simplicity, we just keep parsing. A true backpressure implementation
                    // would pause reading.
                    // Thread.sleep or wait() could be used here.
                  }
                  if (line.trim().isEmpty()) continue;

                  try {
                    JsonObject message = new JsonObject(line);
                    if (dataHandler != null) {
                      context.runOnContext(v -> dataHandler.handle(message));
                    }
                  } catch (Exception e) {
                    log.warn("Failed to parse MCP message: {}", line, e);
                    if (exceptionHandler != null) {
                      context.runOnContext(v -> exceptionHandler.handle(e));
                    }
                  }
                }
              } catch (Exception e) {
                if (exceptionHandler != null) {
                  context.runOnContext(v -> exceptionHandler.handle(e));
                }
              } finally {
                if (endHandler != null) {
                  context.runOnContext(v -> endHandler.handle(null));
                }
              }
            },
            "mcp-stdio-reader-" + command.get(0));
    readThread.setDaemon(true);
    readThread.start();

    // Also consume stderr to prevent blocking
    errorReadThread =
        new Thread(
            () -> {
              try (BufferedReader reader =
                  new BufferedReader(
                      new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                  log.error("[MCP-STDERR] {}", line);
                }
              } catch (Exception e) {
                log.trace("Stderr stream closed", e);
              }
            },
            "mcp-stdio-error-reader-" + command.get(0));
    errorReadThread.setDaemon(true);
    errorReadThread.start();
  }

  @Override
  public Future<Void> send(JsonObject message) {
    return vertx.executeBlocking(
        () -> {
          if (writer != null) {
            writer.write(message.encode());
            writer.write("\n");
            writer.flush();
            return null;
          } else {
            throw new IllegalStateException("Transport not connected");
          }
        });
  }

  @Override
  public ReadStream<JsonObject> messageStream() {
    return this;
  }

  @Override
  public Future<Void> close() {
    return vertx.executeBlocking(
        () -> {
          if (process != null) {
            process.destroy();
          }
          if (readThread != null) {
            readThread.interrupt();
          }
          if (errorReadThread != null) {
            errorReadThread.interrupt();
          }
          return null;
        });
  }

  @Override
  public ReadStream<JsonObject> exceptionHandler(Handler<Throwable> handler) {
    this.exceptionHandler = handler;
    return this;
  }

  @Override
  public ReadStream<JsonObject> handler(Handler<JsonObject> handler) {
    this.dataHandler = handler;
    return this;
  }

  @Override
  public ReadStream<JsonObject> pause() {
    this.paused.set(true);
    return this;
  }

  @Override
  public ReadStream<JsonObject> resume() {
    this.paused.set(false);
    return this;
  }

  @Override
  public ReadStream<JsonObject> fetch(long amount) {
    // Basic implementation, ignores exact amount for now
    resume();
    return this;
  }

  @Override
  public ReadStream<JsonObject> endHandler(Handler<Void> endHandler) {
    this.endHandler = endHandler;
    return this;
  }
}
