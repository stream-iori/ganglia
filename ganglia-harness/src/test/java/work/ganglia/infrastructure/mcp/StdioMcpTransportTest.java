package work.ganglia.infrastructure.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
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
    client.close().onComplete(testContext.succeedingThenComplete());
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
}
