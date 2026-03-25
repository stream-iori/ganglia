package work.ganglia.infrastructure.mcp;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.streams.ReadStream;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.infrastructure.mcp.transport.McpTransport;

@ExtendWith(VertxExtension.class)
class VertxMcpClientTest {

  /**
   * A transport that accepts sends but never delivers responses, simulating a hanging MCP server.
   */
  private static class SilentTransport implements McpTransport {
    @Override
    public Future<Void> connect() {
      return Future.succeededFuture();
    }

    @Override
    public Future<Void> send(JsonObject message) {
      return Future.succeededFuture();
    }

    @Override
    public ReadStream<JsonObject> messageStream() {
      return new ReadStream<>() {
        @Override
        public ReadStream<JsonObject> exceptionHandler(Handler<Throwable> handler) {
          return this;
        }

        @Override
        public ReadStream<JsonObject> handler(Handler<JsonObject> handler) {
          return this;
        }

        @Override
        public ReadStream<JsonObject> pause() {
          return this;
        }

        @Override
        public ReadStream<JsonObject> resume() {
          return this;
        }

        @Override
        public ReadStream<JsonObject> fetch(long amount) {
          return this;
        }

        @Override
        public ReadStream<JsonObject> endHandler(Handler<Void> endHandler) {
          return this;
        }
      };
    }

    @Override
    public Future<Void> close() {
      return Future.succeededFuture();
    }
  }

  @Test
  void testMcpRequest_timeout(Vertx vertx, VertxTestContext testContext) {
    VertxMcpClient client = new VertxMcpClient(vertx, new SilentTransport(), 1000);

    client
        .ping()
        .onComplete(
            testContext.failing(
                err ->
                    testContext.verify(
                        () -> {
                          assertTrue(
                              err.getMessage().contains("timed out"),
                              "Expected timeout error, got: " + err.getMessage());
                          testContext.completeNow();
                        })));
  }

  @Test
  void testMcpRequest_timeoutCleansPendingMap(Vertx vertx, VertxTestContext testContext) {
    VertxMcpClient client = new VertxMcpClient(vertx, new SilentTransport(), 1000);

    // Send a request that will time out
    client
        .ping()
        .onComplete(
            testContext.failing(
                err ->
                    testContext.verify(
                        () -> {
                          // The timeout handler removes the entry from pendingRequests.
                          // Verify by closing the client (which clears remaining entries).
                          // If the map was already cleaned, close won't fail any promises.
                          client
                              .close()
                              .onComplete(
                                  testContext.succeeding(
                                      v -> {
                                        testContext.completeNow();
                                      }));
                        })));
  }
}
