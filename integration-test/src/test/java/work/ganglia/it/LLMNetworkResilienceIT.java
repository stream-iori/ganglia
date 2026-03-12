package work.ganglia.it;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import work.ganglia.config.ConfigManager;
import work.ganglia.infrastructure.external.llm.ModelGatewayFactory;
import work.ganglia.port.chat.Message;
import work.ganglia.port.external.llm.ModelGateway;
import work.ganglia.port.external.llm.ModelOptions;
import work.ganglia.port.internal.state.AgentSignal;
import work.ganglia.it.ITExecutionContext;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

@ExtendWith(VertxExtension.class)
public class LLMNetworkResilienceIT {

    private Vertx vertx;
    private HttpServer server;
    private int port = 8089;
    private AtomicInteger requestCount;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp(VertxTestContext testContext) {
        vertx = Vertx.vertx();
        requestCount = new AtomicInteger(0);
        server = vertx.createHttpServer();
        testContext.completeNow();
    }

    @AfterEach
    void tearDown(VertxTestContext testContext) {
        server.close().onComplete(testContext.succeedingThenComplete());
    }

    @Test
    void testTimeoutTriggersRetryAndEventuallyFails(VertxTestContext testContext) throws Exception {
        // Setup a slow server that ignores requests
        server.requestHandler(req -> {
            requestCount.incrementAndGet();
            // Intentionally don't respond to trigger timeout
        }).listen(port).onComplete(testContext.succeeding(s -> {
            
            // Generate a config that points to the slow server
            String tempConfigPath = tempDir.resolve("test-timeout-config.json").toString();
            String configJson = """
                {
                    "models": {
                        "primary": {
                            "name": "test-model",
                            "type": "openai",
                            "baseUrl": "http://localhost:8089",
                            "timeout": 500,
                            "maxRetries": 2
                        }
                    }
                }
                """;
            vertx.fileSystem().writeFileBlocking(tempConfigPath, io.vertx.core.buffer.Buffer.buffer(configJson));

            ConfigManager configManager = new ConfigManager(vertx, tempConfigPath);
            configManager.init().onComplete(testContext.succeeding(v -> {
                ModelGateway gateway = ModelGatewayFactory.create(vertx, configManager);
                
                ModelOptions options = new ModelOptions(0.0, 100, "test-model", true);
                ITExecutionContext context = new ITExecutionContext("test-session");

                long start = System.currentTimeMillis();
                gateway.chatStream(Collections.singletonList(Message.user("Hello")), Collections.emptyList(), options, context, new AgentSignal())
                    .onComplete(testContext.failing(err -> testContext.verify(() -> {
                        long duration = System.currentTimeMillis() - start;
                        
                        // Initial request + 2 retries = 3 total requests
                        assertEquals(3, requestCount.get(), "Should have attempted 3 requests total");
                        
                        // Verify timeout was respected (3 * 500ms + delays)
                        assertTrue(duration > 1500, "Should have taken at least 1500ms");
                        
                        // Verify user-facing warning was emitted
                        boolean hasWarning = context.getStreams().stream().anyMatch(msg -> msg.contains("⚠️ Network error") && msg.toLowerCase().contains("timeout"));
                        assertTrue(hasWarning, "Should have emitted a network warning to the stream");

                        testContext.completeNow();
                    })));
            }));
        }));
    }

    @Test
    void testConnectionResetTriggersRetry(VertxTestContext testContext) throws Exception {
        // Setup a server that immediately closes the connection
        server.requestHandler(req -> {
            int count = requestCount.incrementAndGet();
            if (count < 2) {
                req.connection().close(); // Force connection reset
            } else {
                req.response()
                    .putHeader("Content-Type", "application/json")
                    .end("{\"choices\":[{\"message\":{\"content\":\"Recovered!\"}}]}");
            }
        }).listen(port).onComplete(testContext.succeeding(s -> {
            
            String tempConfigPath = tempDir.resolve("test-reset-config.json").toString();
            String configJson = """
                {
                    "models": {
                        "primary": {
                            "name": "test-model",
                            "type": "openai",
                            "baseUrl": "http://localhost:8089",
                            "timeout": 2000,
                            "maxRetries": 3
                        }
                    }
                }
                """;
            vertx.fileSystem().writeFileBlocking(tempConfigPath, io.vertx.core.buffer.Buffer.buffer(configJson));

            ConfigManager configManager = new ConfigManager(vertx, tempConfigPath);
            configManager.init().onComplete(testContext.succeeding(v -> {
                ModelGateway gateway = ModelGatewayFactory.create(vertx, configManager);
                
                ModelOptions options = new ModelOptions(0.0, 100, "test-model", false); // Test non-stream chat

                gateway.chat(Collections.singletonList(Message.user("Hello")), Collections.emptyList(), options, new AgentSignal())
                    .onComplete(testContext.succeeding(res -> testContext.verify(() -> {
                        assertEquals("Recovered!", res.content());
                        assertEquals(2, requestCount.get());
                        testContext.completeNow();
                    })));
            }));
        }));
    }
}
