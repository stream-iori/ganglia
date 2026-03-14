package work.ganglia.it;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import work.Main;
import work.ganglia.BootstrapOptions;
import work.ganglia.util.Constants;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(VertxExtension.class)
public class StartupSelfCheckIT {

    @Test
    public void testBootstrapCreatesCoreStructure(Vertx vertx, VertxTestContext testContext, @TempDir Path tempDir) {
        String projectRoot = tempDir.toAbsolutePath().toString();
        String configPath = Paths.get(projectRoot, Constants.DEFAULT_CONFIG_FILE).toString();
        
        BootstrapOptions options = BootstrapOptions.defaultOptions()
            .withProjectRoot(projectRoot)
            .withConfigPath(configPath);

        Main.bootstrap(vertx, options)
            .onComplete(testContext.succeeding(ganglia -> {
                testContext.verify(() -> {
                    // Check config.json
                    assertTrue(new File(configPath).exists(), "config.json should be created");
                    
                    // Check directories
                    assertTrue(new File(projectRoot, Constants.DIR_SKILLS).exists(), "skills dir should be created");
                    assertTrue(new File(projectRoot, Constants.DIR_MEMORY).exists(), "memory dir should be created");
                    assertTrue(new File(projectRoot, Constants.DIR_STATE).exists(), "state dir should be created");
                    assertTrue(new File(projectRoot, Constants.DIR_LOGS).exists(), "logs dir should be created");
                    
                    // Check MEMORY.md
                    assertTrue(new File(projectRoot, Constants.FILE_MEMORY_MD).exists(), "MEMORY.md should be created");
                    
                    testContext.completeNow();
                });
            }));
    }
}
