package work.ganglia.web;

import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.Mockito.mock;
import work.ganglia.kernel.loop.StandardAgentLoop;
import work.ganglia.port.internal.state.SessionManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(VertxExtension.class)
public class WebUIVerticleTest {

    @Test
    @DisplayName("Should start WebUI server and serve index.html")
    void shouldStartServer(Vertx vertx, VertxTestContext testContext) {
        WebUIVerticle verticle = new WebUIVerticle(0, mock(StandardAgentLoop.class), mock(SessionManager.class));
        
        vertx.deployVerticle(verticle).onComplete(deploy -> {
            testContext.verify(() -> {
                assertTrue(deploy.succeeded());
                testContext.completeNow();
            });
        });
    }

    @Test
    @DisplayName("Should respond to SockJS info request")
    void shouldRespondToSockJSInfo(Vertx vertx, VertxTestContext testContext) {
        int port = 8081;
        WebUIVerticle verticle = new WebUIVerticle(port, mock(StandardAgentLoop.class), mock(SessionManager.class));
        
        vertx.deployVerticle(verticle).onComplete(deploy -> {
            WebClient client = WebClient.create(vertx);
            client.get(port, "localhost", "/eventbus/info")
                .send()
                .onComplete(response -> {
                    testContext.verify(() -> {
                        assertTrue(response.succeeded());
                        assertEquals(200, response.result().statusCode());
                        assertTrue(response.result().bodyAsString().contains("websocket"));
                        testContext.completeNow();
                    });
                });
        });
    }
}
