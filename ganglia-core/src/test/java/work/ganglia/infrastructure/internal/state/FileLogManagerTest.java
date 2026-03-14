package work.ganglia.infrastructure.internal.state;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import work.ganglia.port.chat.Message;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.chat.Turn;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import work.ganglia.util.Constants;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(VertxExtension.class)
public class FileLogManagerTest {

    @Test
    public void testAppendLogCreatesDirectory(Vertx vertx, VertxTestContext testContext) {
        FileLogManager manager = new FileLogManager(vertx);
        
        SessionContext mockContext = mock(SessionContext.class);
        Turn mockTurn = mock(Turn.class);
        Message mockMsg = Message.user("test message");
        
        when(mockContext.currentTurn()).thenReturn(mockTurn);
        when(mockTurn.getLatestMessage()).thenReturn(mockMsg);
        
        manager.appendLog(mockContext)
            .compose(v -> {
                assertTrue(new File(Constants.DIR_LOGS).exists());
                assertTrue(new File(Constants.DIR_LOGS).isDirectory());
                return vertx.fileSystem().deleteRecursive(Constants.DEFAULT_GANGLIA_DIR);
            })
            .onComplete(testContext.succeeding(v -> testContext.completeNow()));
    }
}
