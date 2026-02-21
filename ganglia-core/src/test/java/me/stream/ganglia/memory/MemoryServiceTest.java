package me.stream.ganglia.memory;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import me.stream.ganglia.core.model.Message;
import me.stream.ganglia.core.model.Role;
import me.stream.ganglia.core.model.Turn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemoryServiceTest {

    private Vertx vertx;
    @Mock
    private ContextCompressor compressor;
    @Mock
    private DailyRecordManager dailyRecordManager;

    private MemoryService memoryService;

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx();
        memoryService = new MemoryService(vertx, compressor, dailyRecordManager);
    }

    @Test
    void testHandleReflectEvent() throws Exception {
        // Setup Turn data
        Message userMsg = Message.user("Write a file");
        Turn turn = new Turn("turn-1", userMsg, new ArrayList<>(), null);
        
        String sessionId = "session-123";
        String goal = "Write a file";

        when(compressor.reflect(any())).thenReturn(Future.succeededFuture("Summary of writing file"));
        when(dailyRecordManager.record(eq(sessionId), eq(goal), eq("Summary of writing file")))
            .thenReturn(Future.succeededFuture());

        // Publish event to EventBus
        JsonObject event = new JsonObject()
            .put("sessionId", sessionId)
            .put("goal", goal)
            .put("turn", JsonObject.mapFrom(turn));

        vertx.eventBus().publish(MemoryService.ADDRESS_REFLECT, event);

        // Wait a bit for async processing
        Thread.sleep(500);

        // Verify
        verify(compressor, timeout(1000)).reflect(any(Turn.class));
        
        ArgumentCaptor<Turn> turnCaptor = ArgumentCaptor.forClass(Turn.class);
        verify(compressor).reflect(turnCaptor.capture());
        assertEquals("turn-1", turnCaptor.getValue().id());
        assertEquals("Write a file", turnCaptor.getValue().userMessage().content());

        verify(dailyRecordManager, timeout(1000)).record(eq(sessionId), eq(goal), eq("Summary of writing file"));
        
        vertx.close();
    }
}
