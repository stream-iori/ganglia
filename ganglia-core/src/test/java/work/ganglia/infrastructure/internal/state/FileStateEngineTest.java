package work.ganglia.infrastructure.internal.state;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import work.ganglia.port.chat.SessionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import work.ganglia.util.Constants;

import java.io.File;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(VertxExtension.class)
public class FileStateEngineTest {

    @Test
    public void testSaveCreatesDirectoryAndFile(Vertx vertx, VertxTestContext testContext, @TempDir Path tempDir) {
        // We need to override the static STATE_DIR if we want to use tempDir easily, 
        // but FileStateEngine uses Constants.DIR_STATE.
        // However, we can just check if it creates .ganglia/state in the current test run.
        // Better: let's test that it actually creates the directory it's told to.
        
        FileStateEngine engine = new FileStateEngine(vertx);
        SessionContext ctx = engine.createSession();
        
        engine.saveSession(ctx)
            .compose(v -> {
                assertTrue(new File(Constants.DIR_STATE).exists());
                assertTrue(new File(Constants.DIR_STATE).isDirectory());
                String expectedFile = Constants.DIR_STATE + "/session_" + ctx.sessionId() + ".json";
                assertTrue(new File(expectedFile).exists());
                return vertx.fileSystem().deleteRecursive(Constants.DEFAULT_GANGLIA_DIR);
            })
            .onComplete(testContext.succeeding(v -> testContext.completeNow()));
    }
}
