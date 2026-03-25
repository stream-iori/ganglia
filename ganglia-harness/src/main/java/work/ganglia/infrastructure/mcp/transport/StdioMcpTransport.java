package work.ganglia.infrastructure.mcp.transport;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.parsetools.RecordParser;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;

import work.ganglia.util.VertxProcess;

/**
 * MCP transport using standard input/output (stdio) of a child process. Refactored to use {@link
 * VertxProcess} for unified reactive process handling.
 */
public class StdioMcpTransport
    implements McpTransport, ReadStream<JsonObject>, WriteStream<JsonObject> {
  private static final Logger log = LoggerFactory.getLogger(StdioMcpTransport.class);

  private final Vertx vertx;
  private final List<String> command;
  private final Map<String, String> environment;
  private VertxProcess vertxProcess;
  private RecordParser parser;

  private Handler<JsonObject> dataHandler;
  private Handler<Throwable> exceptionHandler;
  private Handler<Void> endHandler;

  public StdioMcpTransport(Vertx vertx, List<String> command, Map<String, String> environment) {
    this.vertx = vertx;
    this.command = command;
    this.environment = environment;
  }

  @Override
  public Future<Void> connect() {
    ProcessBuilder pb = new ProcessBuilder(command);
    if (environment != null) {
      pb.environment().putAll(environment);
    }

    return VertxProcess.spawn(vertx, pb)
        .map(
            vp -> {
              this.vertxProcess = vp;

              // Setup line-based parser for incoming JSON messages
              this.parser = RecordParser.newDelimited("\n", vp.stdout());
              this.parser.handler(
                  buffer -> {
                    String line = buffer.toString().trim();
                    if (line.isEmpty()) return;

                    try {
                      JsonObject message = new JsonObject(line);
                      if (dataHandler != null) {
                        dataHandler.handle(message);
                      }
                    } catch (Exception e) {
                      log.warn("Failed to parse MCP message: {}", line, e);
                      if (exceptionHandler != null) {
                        exceptionHandler.handle(e);
                      }
                    }
                  });

              this.parser.exceptionHandler(
                  e -> {
                    if (exceptionHandler != null) exceptionHandler.handle(e);
                  });

              this.parser.endHandler(
                  v -> {
                    if (endHandler != null) endHandler.handle(null);
                  });

              // Handle stderr
              vp.stderr().handler(buf -> log.error("[MCP-STDERR] {}", buf.toString().trim()));

              return null;
            });
  }

  @Override
  public Future<Void> send(JsonObject message) {
    return write(message);
  }

  @Override
  public ReadStream<JsonObject> messageStream() {
    return this;
  }

  @Override
  public Future<Void> close() {
    if (vertxProcess != null) {
      vertxProcess.destroy();
    }
    return Future.succeededFuture();
  }

  @Override
  public StdioMcpTransport exceptionHandler(Handler<Throwable> handler) {
    this.exceptionHandler = handler;
    return this;
  }

  @Override
  public StdioMcpTransport handler(Handler<JsonObject> handler) {
    this.dataHandler = handler;
    return this;
  }

  @Override
  public StdioMcpTransport pause() {
    if (parser != null) parser.pause();
    return this;
  }

  @Override
  public StdioMcpTransport resume() {
    if (parser != null) parser.resume();
    return this;
  }

  @Override
  public StdioMcpTransport fetch(long amount) {
    if (parser != null) parser.fetch(amount);
    return this;
  }

  @Override
  public StdioMcpTransport endHandler(Handler<Void> endHandler) {
    this.endHandler = endHandler;
    return this;
  }

  // --- WriteStream implementation ---

  @Override
  public Future<Void> write(JsonObject data) {
    if (vertxProcess == null) {
      return Future.failedFuture("Transport not connected");
    }
    return vertxProcess.stdin().write(Buffer.buffer(data.encode() + "\n"));
  }

  @Override
  public Future<Void> end() {
    return close();
  }

  @Override
  public StdioMcpTransport setWriteQueueMaxSize(int maxSize) {
    if (vertxProcess != null) {
      vertxProcess.stdin().setWriteQueueMaxSize(maxSize);
    }
    return this;
  }

  @Override
  public boolean writeQueueFull() {
    return vertxProcess != null && vertxProcess.stdin().writeQueueFull();
  }

  @Override
  public StdioMcpTransport drainHandler(Handler<Void> handler) {
    if (vertxProcess != null) {
      vertxProcess.stdin().drainHandler(handler);
    }
    return this;
  }
}
