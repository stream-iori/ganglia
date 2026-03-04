package work.ganglia.tools;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import work.ganglia.core.model.SessionContext;
import work.ganglia.tools.model.ToolCall;
import work.ganglia.tools.model.ToDoList;
import work.ganglia.tools.model.ToolInvokeResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(VertxExtension.class)
class WebFetchToolsTest {

    private WebFetchTools tools;
    private SessionContext context;

    @BeforeEach
    void setUp(Vertx vertx) {
        tools = new WebFetchTools(vertx);
        context = new SessionContext(UUID.randomUUID().toString(), Collections.emptyList(), null, Collections.emptyMap(), Collections.emptyList(), null, ToDoList.empty());
    }

    @Test
    void testWebFetch(Vertx vertx, VertxTestContext testContext) {
        // Start a local server to test against
        HttpServer server = vertx.createHttpServer();
        server.requestHandler(req -> req.response().end("Hello from Ganglia!"))
            .listen(0)
            .onComplete(testContext.succeeding(s -> {
                int port = s.actualPort();
                String url = "http://localhost:" + port;

                tools.execute(new ToolCall("id", "web_fetch", Map.of("url", url)), context)
                    .onComplete(testContext.succeeding(result -> {
                        testContext.verify(() -> {
                            assertEquals(ToolInvokeResult.Status.SUCCESS, result.status());
                            assertTrue(result.output().contains("Hello from Ganglia!"));
                            s.close();
                            testContext.completeNow();
                        });
                    }));
            }));
    }
}
