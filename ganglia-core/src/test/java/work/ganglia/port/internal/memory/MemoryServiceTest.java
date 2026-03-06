package work.ganglia.port.internal.memory;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import work.ganglia.port.chat.Message;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.internal.state.TokenUsage;
import work.ganglia.port.chat.Turn;
import work.ganglia.stubs.StubConfigManager;
import work.ganglia.stubs.StubModelGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.Collections;

import work.ganglia.port.internal.memory.ContextCompressor;
import work.ganglia.infrastructure.internal.memory.DefaultContextCompressor;
import work.ganglia.port.internal.memory.DailyRecordManager;
import work.ganglia.infrastructure.internal.memory.FileSystemDailyRecordManager;
import work.ganglia.infrastructure.internal.memory.DailyJournalModule;

@ExtendWith(VertxExtension.class)
class MemoryServiceTest {

    private ContextCompressor compressor;
    private DailyRecordManager dailyRecordManager;
    private MemoryService memoryService;
    private StubModelGateway modelGateway;
    private StubConfigManager configManager;
    private final String TEST_MEMORY_PATH = "target/test-memory";

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext) {
        this.modelGateway = new StubModelGateway();
        this.configManager = new StubConfigManager(vertx);
        this.compressor = new DefaultContextCompressor(modelGateway, configManager);
        this.dailyRecordManager = new FileSystemDailyRecordManager(vertx, TEST_MEMORY_PATH);
        this.memoryService = new MemoryService(vertx);
        this.memoryService.registerModule(new DailyJournalModule(compressor, dailyRecordManager));

        // Ensure clean directory
        vertx.fileSystem().deleteRecursive(TEST_MEMORY_PATH)
            .recover(err -> io.vertx.core.Future.succeededFuture()) // Ignore if not exists
            .onComplete(ar -> testContext.completeNow());
    }

    @AfterEach
    void tearDown(Vertx vertx, VertxTestContext testContext) {
        vertx.fileSystem().deleteRecursive(TEST_MEMORY_PATH)
             .recover(err -> io.vertx.core.Future.succeededFuture())
             .onComplete(ar -> testContext.completeNow());
    }

    @Test
    void testHandleReflectEvent(Vertx vertx, VertxTestContext testContext) {
        // Setup Turn data
        Message userMsg = Message.user("Write a file");
        Turn turn = new Turn("turn-1", userMsg, new ArrayList<>(), null);

        String sessionId = "session-123";
        String goal = "Write a file";
        String accomplishment = "Summary of writing file";

        // Mock LLM response for reflection
        ModelResponse reflectionResponse = new ModelResponse(accomplishment, Collections.emptyList(), new TokenUsage(10, 10));
        modelGateway.addResponse(reflectionResponse);

        // Publish event to EventBus
        MemoryEvent event = new MemoryEvent(MemoryEvent.EventType.TURN_COMPLETED, sessionId, goal, turn);

        vertx.eventBus().publish(MemoryService.ADDRESS_EVENT, JsonObject.mapFrom(event));

        // Verify by checking file system eventually
        testContext.verify(() -> {
            vertx.setPeriodic(100, id -> {
                vertx.fileSystem().readDir(TEST_MEMORY_PATH)
                    .onSuccess(files -> {
                        if (!files.isEmpty()) {
                            vertx.cancelTimer(id);
                            // Check content
                            String file = files.get(0);
                            vertx.fileSystem().readFile(file)
                                .onSuccess(buffer -> {
                                    String content = buffer.toString();
                                    if (content.contains(accomplishment) && content.contains(sessionId)) {
                                        testContext.completeNow();
                                    }
                                });
                        }
                    });
            });

            // Timeout if not found
            vertx.setTimer(2000, id -> {
                if (!testContext.completed()) {
                    testContext.failNow("Timeout waiting for daily record file");
                }
            });
        });
    }
}
