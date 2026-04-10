package work.ganglia.infrastructure.mcp.transport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.http.WebSocketConnectOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.streams.ReadStream;

public class StreamMcpTransport implements McpTransport, ReadStream<JsonObject> {
  private static final Logger log = LoggerFactory.getLogger(StreamMcpTransport.class);

  private final Vertx vertx;
  private final WebSocketConnectOptions options;
  private WebSocketClient client;
  private WebSocket webSocket;

  private Handler<JsonObject> dataHandler;
  private Handler<Throwable> exceptionHandler;
  private Handler<Void> endHandler;

  public StreamMcpTransport(Vertx vertx, WebSocketConnectOptions options) {
    this.vertx = vertx;
    this.options = options;
  }

  @Override
  public Future<Void> connect() {
    this.client = vertx.createWebSocketClient();
    return client
        .connect(options)
        .compose(
            ws -> {
              this.webSocket = ws;

              ws.textMessageHandler(
                  text -> {
                    try {
                      JsonObject msg = new JsonObject(text);
                      if (dataHandler != null) {
                        dataHandler.handle(msg);
                      }
                    } catch (Exception e) {
                      log.warn("Failed to parse MCP stream message: {}", text, e);
                      if (exceptionHandler != null) {
                        exceptionHandler.handle(e);
                      }
                    }
                  });

              ws.exceptionHandler(
                  e -> {
                    if (exceptionHandler != null) exceptionHandler.handle(e);
                  });

              ws.closeHandler(
                  v -> {
                    if (endHandler != null) endHandler.handle(null);
                  });

              return Future.succeededFuture();
            });
  }

  @Override
  public Future<Void> send(JsonObject message) {
    if (webSocket != null && !webSocket.isClosed()) {
      return webSocket.writeTextMessage(message.encode());
    }
    return Future.failedFuture("WebSocket is not connected");
  }

  @Override
  public ReadStream<JsonObject> messageStream() {
    return this;
  }

  @Override
  public Future<Void> close() {
    if (webSocket != null && !webSocket.isClosed()) {
      return webSocket.close();
    }
    return Future.succeededFuture();
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
    if (webSocket != null) webSocket.pause();
    return this;
  }

  @Override
  public ReadStream<JsonObject> resume() {
    if (webSocket != null) webSocket.resume();
    return this;
  }

  @Override
  public ReadStream<JsonObject> fetch(long amount) {
    if (webSocket != null) webSocket.fetch(amount);
    return this;
  }

  @Override
  public ReadStream<JsonObject> endHandler(Handler<Void> endHandler) {
    this.endHandler = endHandler;
    return this;
  }
}
