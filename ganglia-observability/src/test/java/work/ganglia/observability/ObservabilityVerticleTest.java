package work.ganglia.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

@ExtendWith(VertxExtension.class)
public class ObservabilityVerticleTest {

  @Test
  @DisplayName("Should start Observability Studio and serve UI")
  void shouldStartServer(Vertx vertx, VertxTestContext testContext) {
    ObservabilityVerticle verticle = new ObservabilityVerticle(0, "webroot");

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
  @DisplayName("Should list trace files from .ganglia/trace")
  void shouldListTraceFiles(Vertx vertx, VertxTestContext testContext) throws IOException {
    Path traceDir = Path.of(".ganglia/trace");
    Files.createDirectories(traceDir);
    Path traceFile = traceDir.resolve("test-trace-obs.jsonl");
    Files.writeString(traceFile, "{\"type\":\"TEST\"}\n");

    ObservabilityVerticle verticle = new ObservabilityVerticle(0, "webroot");

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
                              assertTrue(arr.contains("test-trace-obs.jsonl"));
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
    Path traceFile = traceDir.resolve("test-trace-obs-2.jsonl");
    Files.writeString(traceFile, "{\"type\":\"TEST_1\"}\n{\"type\":\"TEST_2\"}\n");

    ObservabilityVerticle verticle = new ObservabilityVerticle(0, "webroot");

    vertx
        .deployVerticle(verticle)
        .onComplete(
            deploy -> {
              int port = verticle.getActualPort();
              WebClient.create(vertx)
                  .get(port, "localhost", "/api/traces/test-trace-obs-2.jsonl")
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
