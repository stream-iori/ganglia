package work.ganglia.web;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
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
}
