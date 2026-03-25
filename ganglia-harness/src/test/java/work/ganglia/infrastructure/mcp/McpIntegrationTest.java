package work.ganglia.infrastructure.mcp;

import static org.junit.jupiter.api.Assertions.*;

import io.vertx.core.Vertx;
import io.vertx.core.http.WebSocketConnectOptions;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import work.ganglia.infrastructure.external.tool.model.ToolInvokeResult;
import work.ganglia.infrastructure.mcp.transport.StreamMcpTransport;
import work.ganglia.port.mcp.McpClient;
import work.ganglia.port.mcp.McpInitializeRequest;

@ExtendWith(VertxExtension.class)
public class McpIntegrationTest {

  private VertxMcpServer server;
  private McpClient client;
  private int port = 8085;

  @BeforeEach
  void setUp(Vertx vertx, VertxTestContext testContext) {
    server = new VertxMcpServer(vertx, port);

    server
        .start()
        .onComplete(
            testContext.succeeding(
                v -> {
                  StreamMcpTransport transport =
                      new StreamMcpTransport(
                          vertx,
                          new WebSocketConnectOptions()
                              .setHost("localhost")
                              .setPort(port)
                              .setURI("/"));
                  client = new VertxMcpClient(vertx, transport);
                  transport.connect().onComplete(testContext.succeedingThenComplete());
                }));
  }

  @AfterEach
  void tearDown(Vertx vertx, VertxTestContext testContext) {
    client.close().onComplete(v -> server.close().onComplete(testContext.succeedingThenComplete()));
  }

  @Test
  void testInitializeAndListTools(Vertx vertx, VertxTestContext testContext) {
    McpInitializeRequest.Implementation clientInfo =
        new McpInitializeRequest.Implementation("TestClient", "1.0");
    McpInitializeRequest initReq =
        new McpInitializeRequest("2025-11-25", new HashMap<>(), clientInfo);

    client
        .initialize(initReq)
        .compose(
            initRes -> {
              assertEquals("2025-11-25", initRes.protocolVersion());
              return client.listTools();
            })
        .onComplete(
            testContext.succeeding(
                listRes -> {
                  testContext.verify(
                      () -> {
                        assertEquals(1, listRes.tools().size());
                        assertEquals("echo", listRes.tools().get(0).name());
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testPing(Vertx vertx, VertxTestContext testContext) {
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
  void testCallToolThroughToolSet(Vertx vertx, VertxTestContext testContext) {
    McpInitializeRequest.Implementation clientInfo =
        new McpInitializeRequest.Implementation("TestClient", "1.0");
    McpInitializeRequest initReq =
        new McpInitializeRequest("2025-11-25", new HashMap<>(), clientInfo);

    client
        .initialize(initReq)
        .compose(v -> McpToolSet.create(client))
        .compose(
            toolSet -> {
              assertEquals(1, toolSet.getDefinitions().size());
              Map<String, Object> args = new HashMap<>();
              args.put("text", "hello world");
              return toolSet.execute("echo", args, null, null);
            })
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertEquals(ToolInvokeResult.Status.SUCCESS, result.status());
                        assertEquals("Echo: hello world", result.output());
                        testContext.completeNow();
                      });
                }));
  }
}
