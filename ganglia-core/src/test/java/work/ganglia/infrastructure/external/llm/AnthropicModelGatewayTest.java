package work.ganglia.infrastructure.external.llm;

import static org.junit.jupiter.api.Assertions.*;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import work.ganglia.port.chat.Message;
import work.ganglia.port.external.llm.ChatRequest;
import work.ganglia.port.external.llm.ModelOptions;
import work.ganglia.port.internal.state.AgentSignal;
import work.ganglia.stubs.StubExecutionContext;

@ExtendWith(VertxExtension.class)
class AnthropicModelGatewayTest {

  private Vertx vertx;
  private HttpServer server;
  private AnthropicModelGateway gateway;
  private int port = 8082;

  @BeforeEach
  void setUp(VertxTestContext testContext) {
    vertx = Vertx.vertx();
    server = vertx.createHttpServer();
    gateway =
        new AnthropicModelGateway(
            vertx, WebClient.create(vertx), "test-key", "http://localhost:" + port);

    server
        .requestHandler(
            req -> {
              if (req.path().equals("/v1/messages")) {
                assertEquals("test-key", req.getHeader("x-api-key"));
                assertEquals("2023-06-01", req.getHeader("anthropic-version"));

                req.bodyHandler(
                    body -> {
                      JsonObject requestPayload = body.toJsonObject();

                      if (requestPayload.getBoolean("stream", false)) {
                        // Stream response
                        req.response()
                            .putHeader("Content-Type", "text/event-stream")
                            .setChunked(true);
                        req.response()
                            .write(
                                "data: {\"type\":\"message_start\",\"message\":{\"usage\":{\"input_tokens\":10}}}\n\n");
                        req.response()
                            .write(
                                "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"Hello \"}}\n\n");

                        vertx.setTimer(
                            100,
                            id -> {
                              if (!req.response().ended()) {
                                req.response()
                                    .write(
                                        "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"World!\"}}\n\n");
                                req.response()
                                    .write(
                                        "data: {\"type\":\"message_delta\",\"usage\":{\"output_tokens\":15},\"delta\":{\"stop_reason\":\"end_turn\"}}\n\n");
                                req.response().write("data: {\"type\":\"message_stop\"}\n\n");
                                req.response().end();
                              }
                            });
                      } else {
                        // Normal response
                        JsonObject response =
                            new JsonObject()
                                .put(
                                    "content",
                                    new JsonArray()
                                        .add(
                                            new JsonObject()
                                                .put("type", "text")
                                                .put("text", "Hello World!")))
                                .put(
                                    "usage",
                                    new JsonObject()
                                        .put("input_tokens", 10)
                                        .put("output_tokens", 15));
                        req.response()
                            .putHeader("Content-Type", "application/json")
                            .end(response.encode());
                      }
                    });
              } else {
                req.response().setStatusCode(404).end();
              }
            })
        .listen(port)
        .onComplete(testContext.succeeding(s -> testContext.completeNow()));
  }

  @AfterEach
  void tearDown(VertxTestContext testContext) {
    server.close().onComplete(v -> vertx.close().onComplete(vv -> testContext.completeNow()));
  }

  @Test
  void testChat(VertxTestContext testContext) {
    List<Message> history = List.of(Message.user("Hello"));
    ModelOptions options = new ModelOptions(0.0, 1024, "claude-3-5-sonnet", false);

    gateway
        .chat(new ChatRequest(history, Collections.emptyList(), options, new AgentSignal()))
        .onComplete(
            testContext.succeeding(
                response -> {
                  testContext.verify(
                      () -> {
                        assertEquals("Hello World!", response.content());
                        assertEquals(10, response.usage().promptTokens());
                        assertEquals(15, response.usage().completionTokens());
                        assertTrue(response.toolCalls().isEmpty());
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testChatStream(VertxTestContext testContext) {
    List<Message> history = List.of(Message.user("Stream this"));
    ModelOptions options = new ModelOptions(0.0, 1024, "claude-3-5-sonnet", true);
    String sessionId = "test-session";

    gateway
        .chatStream(
            new ChatRequest(history, Collections.emptyList(), options, new AgentSignal()),
            new StubExecutionContext(sessionId))
        .onComplete(
            testContext.succeeding(
                response -> {
                  testContext.verify(
                      () -> {
                        assertEquals("Hello World!", response.content());
                        assertEquals(10, response.usage().promptTokens());
                        assertEquals(15, response.usage().completionTokens());
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testChatStreamCancellation(VertxTestContext testContext) {
    List<Message> history = List.of(Message.user("Long stream"));
    ModelOptions options = new ModelOptions(0.0, 1024, "claude-3-5-sonnet", true);
    String sessionId = "cancel-session";
    AgentSignal signal = new AgentSignal();

    // Register a consumer to check if tokens are still being published after abort
    java.util.concurrent.atomic.AtomicInteger tokenCount =
        new java.util.concurrent.atomic.AtomicInteger(0);
    StubExecutionContext execContext =
        new StubExecutionContext(
            sessionId,
            chunk -> {
              tokenCount.incrementAndGet();
              signal.abort();
            });

    gateway
        .chatStream(new ChatRequest(history, Collections.emptyList(), options, signal), execContext)
        .onComplete(
            testContext.failing(
                err -> {
                  testContext.verify(
                      () -> {
                        assertTrue(err instanceof work.ganglia.kernel.loop.AgentAbortedException);
                        assertTrue(tokenCount.get() < 3);
                        testContext.completeNow();
                      });
                }));
  }
}
