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
class OpenAiModelGatewayTest {

  private Vertx vertx;
  private HttpServer server;
  private OpenAiModelGateway gateway;
  private int port = 8081;

  @BeforeEach
  void setUp(VertxTestContext testContext) {
    vertx = Vertx.vertx();
    server = vertx.createHttpServer();
    gateway =
        new OpenAiModelGateway(
            vertx, WebClient.create(vertx), "test-key", "http://localhost:" + port);

    server
        .requestHandler(
            req -> {
              if (req.path().equals("/chat/completions")) {
                assertEquals("Bearer test-key", req.getHeader("Authorization"));

                req.bodyHandler(
                    body -> {
                      JsonObject requestPayload = body.toJsonObject();

                      if (requestPayload.getBoolean("stream", false)) {
                        req.response()
                            .putHeader("Content-Type", "text/event-stream")
                            .setChunked(true);
                        req.response()
                            .write(
                                "data: {\"choices\":[{\"delta\":{\"content\":\"Hello \"}}]}\n\n");
                        vertx.setTimer(
                            100,
                            id -> {
                              if (!req.response().ended()) {
                                req.response()
                                    .write(
                                        "data: {\"choices\":[{\"delta\":{\"content\":\"OpenAI!\"}}]}\n\n");
                                req.response()
                                    .write(
                                        "data: {\"usage\":{\"prompt_tokens\":5,\"completion_tokens\":10}}\n\n");
                                req.response().write("data: [DONE]\n\n");
                                req.response().end();
                              }
                            });
                      } else {
                        JsonObject response =
                            new JsonObject()
                                .put(
                                    "choices",
                                    new JsonArray()
                                        .add(
                                            new JsonObject()
                                                .put(
                                                    "message",
                                                    new JsonObject()
                                                        .put("content", "Hello OpenAI!"))))
                                .put(
                                    "usage",
                                    new JsonObject()
                                        .put("prompt_tokens", 5)
                                        .put("completion_tokens", 10));
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
    ModelOptions options = new ModelOptions(0.0, 1024, "gpt-4", false);

    gateway
        .chat(new ChatRequest(history, Collections.emptyList(), options, new AgentSignal()))
        .onComplete(
            testContext.succeeding(
                response -> {
                  testContext.verify(
                      () -> {
                        assertEquals("Hello OpenAI!", response.content());
                        assertEquals(5, response.usage().promptTokens());
                        assertEquals(10, response.usage().completionTokens());
                        assertTrue(response.toolCalls().isEmpty());
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testChatStream(VertxTestContext testContext) {
    List<Message> history = List.of(Message.user("Stream this"));
    ModelOptions options = new ModelOptions(0.0, 1024, "gpt-4", true);
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
                        assertEquals("Hello OpenAI!", response.content());
                        assertEquals(5, response.usage().promptTokens());
                        assertEquals(10, response.usage().completionTokens());
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testChatStreamCancellation(VertxTestContext testContext) {
    List<Message> history = List.of(Message.user("Long stream"));
    ModelOptions options = new ModelOptions(0.0, 1024, "gpt-4", true);
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
                        // We should have received exactly 1 token (the one that triggered the
                        // abort)
                        // or at least not ALL of them.
                        assertTrue(tokenCount.get() < 3);
                        testContext.completeNow();
                      });
                }));
  }
}
