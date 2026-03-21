package work.ganglia.infrastructure.mcp;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import work.ganglia.util.Constants;

@ExtendWith(VertxExtension.class)
public class McpConfigManagerTest {

  private String testProjectRoot = "target/test-mcp-project";
  private Path mcpConfigPath;

  @BeforeEach
  void setUp(Vertx vertx, VertxTestContext testContext) {
    mcpConfigPath =
        Paths.get(testProjectRoot, Constants.DEFAULT_GANGLIA_DIR, McpConfigManager.FILE_MCP_JSON);
    vertx
        .fileSystem()
        .mkdirs(mcpConfigPath.getParent().toString())
        .onComplete(testContext.succeedingThenComplete());
  }

  @AfterEach
  void tearDown(Vertx vertx, VertxTestContext testContext) {
    vertx
        .fileSystem()
        .deleteRecursive(testProjectRoot)
        .recover(err -> io.vertx.core.Future.succeededFuture())
        .onComplete(testContext.succeedingThenComplete());
  }

  @Test
  void testCreatesDefaultConfigWhenMissing(Vertx vertx, VertxTestContext testContext) {
    McpConfigManager.loadMcpToolSets(vertx, testProjectRoot)
        .onComplete(
            testContext.succeeding(
                registry -> {
                  testContext.verify(
                      () -> {
                        assertTrue(registry.toolSets().isEmpty());
                        assertTrue(registry.clients().isEmpty());
                        assertTrue(Files.exists(mcpConfigPath));

                        String content = Files.readString(mcpConfigPath);
                        JsonObject json = new JsonObject(content);
                        assertTrue(json.containsKey("mcpServers"));

                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testLoadsEmptyConfig(Vertx vertx, VertxTestContext testContext) throws Exception {
    JsonObject config = new JsonObject().put("mcpServers", new JsonObject());
    Files.writeString(mcpConfigPath, config.encode());

    McpConfigManager.loadMcpToolSets(vertx, testProjectRoot)
        .onComplete(
            testContext.succeeding(
                registry -> {
                  testContext.verify(
                      () -> {
                        assertTrue(registry.toolSets().isEmpty());
                        assertTrue(registry.clients().isEmpty());
                        testContext.completeNow();
                      });
                }));
  }
}
