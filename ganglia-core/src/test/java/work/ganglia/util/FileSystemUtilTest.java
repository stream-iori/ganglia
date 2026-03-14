package work.ganglia.util;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.File;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
public class FileSystemUtilTest {

    @Test
    public void testEnsureDirectoryExists(Vertx vertx, VertxTestContext testContext) {
        String testDir = "target/test-dir-" + System.currentTimeMillis();
        
        FileSystemUtil.ensureDirectoryExists(vertx, testDir)
            .compose(v -> {
                assertTrue(new File(testDir).exists());
                assertTrue(new File(testDir).isDirectory());
                // Test second call (already exists)
                return FileSystemUtil.ensureDirectoryExists(vertx, testDir);
            })
            .onComplete(testContext.succeeding(v -> testContext.completeNow()));
    }

    @Test
    public void testEnsureFileWithDefault(Vertx vertx, VertxTestContext testContext) {
        String testFile = "target/test-subdir-" + System.currentTimeMillis() + "/test-file.txt";
        String content = "Hello World";
        
        FileSystemUtil.ensureFileWithDefault(vertx, testFile, content)
            .compose(v -> {
                assertTrue(new File(testFile).exists());
                return vertx.fileSystem().readFile(testFile);
            })
            .compose(buffer -> {
                assertEquals(content, buffer.toString());
                // Test second call (should not overwrite)
                return FileSystemUtil.ensureFileWithDefault(vertx, testFile, "Should not be written");
            })
            .compose(v -> vertx.fileSystem().readFile(testFile))
            .onComplete(testContext.succeeding(buffer -> {
                assertEquals(content, buffer.toString());
                testContext.completeNow();
            }));
    }
}
