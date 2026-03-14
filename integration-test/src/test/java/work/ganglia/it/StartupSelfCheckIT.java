package work.ganglia.it;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import work.Main;
import work.ganglia.Ganglia;
import work.ganglia.BootstrapOptions;
import work.ganglia.coding.tool.CodingToolsFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(VertxExtension.class)
public class StartupSelfCheckIT {

    @Test
    void testStartupStructureCreation(Vertx vertx, VertxTestContext testContext, @TempDir Path tempDir) {
        String projectRoot = tempDir.toAbsolutePath().toString();
        CodingToolsFactory codingToolsFactory = new CodingToolsFactory(vertx, projectRoot);

        BootstrapOptions options = BootstrapOptions.defaultOptions()
            .withProjectRoot(projectRoot)
            .withOverrideConfig(new JsonObject().put("webui", new JsonObject().put("enabled", false)))
            .withExtraToolSets(codingToolsFactory.createToolSets())
            .withExtraContextSources(codingToolsFactory.createContextSources());

        Main.bootstrap(vertx, options)
            .onComplete(testContext.succeeding(ganglia -> {
                testContext.verify(() -> {
                    assertTrue(Files.exists(tempDir.resolve(".ganglia/skills")));
                    assertTrue(Files.exists(tempDir.resolve(".ganglia/memory")));
                    assertTrue(Files.exists(tempDir.resolve(".ganglia/state")));
                    assertTrue(Files.exists(tempDir.resolve(".ganglia/logs")));
                    assertTrue(Files.exists(tempDir.resolve("MEMORY.md")));
                    testContext.completeNow();
                });
            }));
    }
}
