package work.ganglia.observability.metrics;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class MetricsHandlerTest {

  @Test
  void returnsJsonSnapshot(Vertx vertx, VertxTestContext testContext) {
    MetricAggregator aggregator = new MetricAggregator(vertx);

    // Seed some data
    aggregator.processEvent(new JsonObject().put("type", "CONTEXT_COMPRESSED"));
    aggregator.processEvent(
        new JsonObject()
            .put("type", "TOKEN_USAGE_RECORDED")
            .put("data", new JsonObject().put("promptTokens", 42).put("completionTokens", 10)));

    Router router = Router.router(vertx);
    router.get("/api/metrics").handler(new MetricsHandler(aggregator));

    vertx
        .createHttpServer()
        .requestHandler(router)
        .listen(0)
        .onSuccess(
            server -> {
              WebClient client = WebClient.create(vertx);
              client
                  .get(server.actualPort(), "localhost", "/api/metrics")
                  .send()
                  .onSuccess(
                      response ->
                          testContext.verify(
                              () -> {
                                assertEquals(200, response.statusCode());
                                assertEquals(
                                    "application/json", response.getHeader("content-type"));

                                JsonObject body = response.bodyAsJsonObject();
                                assertNotNull(body.getJsonObject("counters"));
                                assertNotNull(body.getJsonObject("histograms"));

                                assertEquals(
                                    1L,
                                    body.getJsonObject("counters")
                                        .getLong("CONTEXT_COMPRESSION_COUNT"));
                                assertEquals(
                                    42L,
                                    body.getJsonObject("counters").getLong("TOKEN_PROMPT_TOTAL"));

                                testContext.completeNow();
                              }))
                  .onFailure(testContext::failNow);
            })
        .onFailure(testContext::failNow);
  }
}
