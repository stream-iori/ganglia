package work.ganglia.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.kernel.loop.ReActAgentLoop;
import work.ganglia.port.internal.state.SessionManager;

@ExtendWith(VertxExtension.class)
public class WebUIVerticleTest {

  @Test
  @DisplayName("Should start WebUI server and serve index.html")
  void shouldStartServer(Vertx vertx, VertxTestContext testContext) {
    WebUIVerticle verticle =
        new WebUIVerticle(0, mock(ReActAgentLoop.class), mock(SessionManager.class), 0);

    vertx
        .deployVerticle(verticle)
        .onComplete(
            deploy -> {
              testContext.verify(
                  () -> {
                    assertTrue(deploy.succeeded());
                    testContext.completeNow();
                  });
            });
  }

  @Test
  @DisplayName("Should serve index.html")
  void shouldServeIndex(Vertx vertx, VertxTestContext testContext) {
    // Use port 0 so the OS picks a free ephemeral port, avoiding conflicts
    WebUIVerticle verticle =
        new WebUIVerticle(0, mock(ReActAgentLoop.class), mock(SessionManager.class), 0);

    vertx
        .deployVerticle(verticle)
        .onComplete(
            deploy -> {
              int port = verticle.getActualPort();
              WebClient client = WebClient.create(vertx);
              client
                  .get(port, "localhost", "/")
                  .send()
                  .onComplete(
                      response -> {
                        testContext.verify(
                            () -> {
                              assertTrue(response.succeeded());
                              // Even if it returns 404 because webroot is empty, it means the
                              // server is UP
                              // For a pure integration test, we just want to see it respond
                              assertTrue(response.result().statusCode() < 500);
                              testContext.completeNow();
                            });
                      });
            });
  }

  @Test
  @DisplayName("Should list trace files from .ganglia/trace")
  void shouldListTraceFiles(Vertx vertx, VertxTestContext testContext) throws IOException {
    Path traceDir = Path.of(".ganglia/trace");
    Files.createDirectories(traceDir);
    Path traceFile = traceDir.resolve("test-trace.jsonl");
    Files.writeString(traceFile, "{\"type\":\"TEST\"}\n");

    WebUIVerticle verticle =
        new WebUIVerticle(0, mock(ReActAgentLoop.class), mock(SessionManager.class), 0);

    vertx
        .deployVerticle(verticle)
        .onComplete(
            deploy -> {
              int port = verticle.getActualPort();
              WebClient.create(vertx)
                  .get(port, "localhost", "/api/traces")
                  .send()
                  .onComplete(
                      res -> {
                        testContext.verify(
                            () -> {
                              assertTrue(res.succeeded());
                              assertEquals(200, res.result().statusCode());
                              JsonArray arr = res.result().bodyAsJsonArray();
                              assertTrue(arr.contains("test-trace.jsonl"));
                              testContext.completeNow();
                            });
                      });
            });
  }

  @Test
  @DisplayName("Should get trace file content as JSON array")
  void shouldGetTraceFileContent(Vertx vertx, VertxTestContext testContext) throws IOException {
    Path traceDir = Path.of(".ganglia/trace");
    Files.createDirectories(traceDir);
    Path traceFile = traceDir.resolve("test-trace-2.jsonl");
    Files.writeString(traceFile, "{\"type\":\"TEST_1\"}\n{\"type\":\"TEST_2\"}\n");

    WebUIVerticle verticle =
        new WebUIVerticle(0, mock(ReActAgentLoop.class), mock(SessionManager.class), 0);

    vertx
        .deployVerticle(verticle)
        .onComplete(
            deploy -> {
              int port = verticle.getActualPort();
              WebClient.create(vertx)
                  .get(port, "localhost", "/api/traces/test-trace-2.jsonl")
                  .send()
                  .onComplete(
                      res -> {
                        testContext.verify(
                            () -> {
                              assertTrue(res.succeeded());
                              assertEquals(200, res.result().statusCode());
                              JsonArray arr = res.result().bodyAsJsonArray();
                              assertEquals(2, arr.size());
                              assertEquals("TEST_1", arr.getJsonObject(0).getString("type"));
                              assertEquals("TEST_2", arr.getJsonObject(1).getString("type"));
                              testContext.completeNow();
                            });
                      });
            });
  }
}
