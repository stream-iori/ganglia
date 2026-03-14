package work.ganglia.infrastructure.internal.memory;

import work.ganglia.port.internal.memory.LongTermMemory;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(VertxExtension.class)
class KnowledgeBaseTest {

    private Vertx vertx;
    private LongTermMemory longTermMemory;
    private static final String TEST_MEMORY_FILE = "target/TEST_MEMORY.md";

    @BeforeEach
    void setUp(Vertx vertx) {
        this.vertx = vertx;
        this.longTermMemory = new FileSystemLongTermMemory(vertx, TEST_MEMORY_FILE);
    }

    @AfterEach
    void tearDown(Vertx vertx, VertxTestContext testContext) {
        vertx.fileSystem().delete(TEST_MEMORY_FILE)
                .onComplete(ar -> testContext.completeNow());
    }

    @Test
    void testInitializeCreatesFile(VertxTestContext testContext) {
        longTermMemory.ensureInitialized()
                .compose(v -> vertx.fileSystem().exists(TEST_MEMORY_FILE))
                .onComplete(testContext.succeeding(exists -> {
                    testContext.verify(() -> {
                        assertTrue(exists, "MEMORY.md should be created");
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void testAppendAndRead(VertxTestContext testContext) {
        String content = "User prefers concise answers.";

        longTermMemory.ensureInitialized()
                .compose(v -> longTermMemory.append("## User Preferences\n" + content))
                .compose(v -> longTermMemory.read())
                .onComplete(testContext.succeeding(readContent -> {
                    testContext.verify(() -> {
                        assertTrue(readContent.contains(content));
                        testContext.completeNow();
                    });
                }));
    }
}
