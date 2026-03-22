package work.ganglia.infrastructure.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import work.ganglia.infrastructure.mcp.transport.StdioMcpTransport;
import work.ganglia.port.mcp.McpClient;
import work.ganglia.port.mcp.McpInitializeRequest;

@ExtendWith(VertxExtension.class)
public class StdioMcpTransportTest {

  private McpClient client;
  private StdioMcpTransport transport;

  @BeforeEach
  void setUp(Vertx vertx, VertxTestContext testContext) {
    String javaHome = System.getProperty("java.home");
    String javaBin = javaHome + File.separator + "bin" + File.separator + "java";
    String classpath = System.getProperty("java.class.path");

    transport =
        new StdioMcpTransport(
            vertx,
            Arrays.asList(javaBin, "-cp", classpath, MockMcpStdioServer.class.getName()),
            null);
    client = new VertxMcpClient(transport);
    transport.connect().onComplete(testContext.succeedingThenComplete());
  }

  @AfterEach
  void tearDown(Vertx vertx, VertxTestContext testContext) {
    if (client != null) {
      client.close().onComplete(testContext.succeedingThenComplete());
    } else {
      testContext.completeNow();
    }
  }

  @Test
  void testStdioInitializeAndListTools(Vertx vertx, VertxTestContext testContext) {
    McpInitializeRequest.Implementation clientInfo =
        new McpInitializeRequest.Implementation("TestClient", "1.0");
    McpInitializeRequest initReq =
        new McpInitializeRequest("2025-11-25", new HashMap<>(), clientInfo);

    client
        .initialize(initReq)
        .compose(
            initRes -> {
              assertEquals("2025-11-25", initRes.protocolVersion());
              assertEquals("MockStdio", initRes.serverInfo().name());
              return client.listTools();
            })
        .onComplete(
            testContext.succeeding(
                listRes -> {
                  testContext.verify(
                      () -> {
                        assertEquals(1, listRes.tools().size());
                        assertEquals("mockTool", listRes.tools().get(0).name());
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testStdioPing(Vertx vertx, VertxTestContext testContext) {
    McpInitializeRequest.Implementation clientInfo =
        new McpInitializeRequest.Implementation("TestClient", "1.0");
    McpInitializeRequest initReq =
        new McpInitializeRequest("2025-11-25", new HashMap<>(), clientInfo);

    client
        .initialize(initReq)
        .compose(v -> client.ping())
        .onComplete(testContext.succeedingThenComplete());
  }

  @Test
  void testWriteBackpressure(Vertx vertx, VertxTestContext testContext) {
    // Set a very small queue size to quickly trigger backpressure
    transport.setWriteQueueMaxSize(10);

    AtomicBoolean queueFullTriggered = new AtomicBoolean(false);
    AtomicBoolean drainTriggered = new AtomicBoolean(false);

    transport.drainHandler(
        v -> {
          drainTriggered.set(true);
          if (queueFullTriggered.get()) {
            testContext.completeNow();
          }
        });

    // Send messages rapidly
    vertx.setTimer(
        100,
        id -> {
          for (int i = 0; i < 100; i++) {
            JsonObject msg =
                new JsonObject().put("jsonrpc", "2.0").put("method", "ping").put("id", i);
            transport.write(msg);
            if (transport.writeQueueFull()) {
              queueFullTriggered.set(true);
            }
          }

          if (!queueFullTriggered.get()) {
            testContext.failNow(new AssertionError("writeQueueFull was never true"));
          }
        });
  }

  @Test
  void testReadBackpressure(Vertx vertx, VertxTestContext testContext) {
    AtomicInteger msgCount = new AtomicInteger(0);

    transport.handler(
        msg -> {
          msgCount.incrementAndGet();
        });

    // Pause immediately
    transport.pause();

    JsonObject pingMsg =
        new JsonObject().put("jsonrpc", "2.0").put("method", "ping").put("id", 1001);

    transport
        .send(pingMsg)
        .onComplete(
            testContext.succeeding(
                v -> {
                  // Wait a bit to ensure no messages are processed while paused
                  vertx.setTimer(
                      500,
                      id1 -> {
                        testContext.verify(
                            () -> {
                              // Because we paused, we shouldn't have processed the ping response
                              // (or any other msg)
                              assertEquals(
                                  0,
                                  msgCount.get(),
                                  "Messages should not be received while paused");

                              // Now resume
                              transport.resume();

                              // Wait a bit to ensure message is processed after resume
                              vertx.setTimer(
                                  500,
                                  id2 -> {
                                    testContext.verify(
                                        () -> {
                                          assertTrue(
                                              msgCount.get() > 0,
                                              "Messages should be received after resume");
                                          testContext.completeNow();
                                        });
                                  });
                            });
                      });
                }));
  }
}
