package me.stream.ganglia.core.memory;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(VertxExtension.class)
class KnowledgeBaseTest {

    private Vertx vertx;
    private KnowledgeBase knowledgeBase;
    private static final String TEST_MEMORY_FILE = "TEST_MEMORY.md";

    @BeforeEach
    void setUp(Vertx vertx) {
        this.vertx = vertx;
        this.knowledgeBase = new KnowledgeBase(vertx, TEST_MEMORY_FILE);
    }

    @AfterEach
    void tearDown(Vertx vertx, VertxTestContext testContext) {
        vertx.fileSystem().delete(TEST_MEMORY_FILE)
                .onComplete(ar -> testContext.completeNow());
    }

    @Test
    void testInitializeCreatesFile(VertxTestContext testContext) {
        knowledgeBase.ensureInitialized()
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
        
        knowledgeBase.ensureInitialized()
                .compose(v -> knowledgeBase.append("## User Preferences\n" + content))
                .compose(v -> knowledgeBase.read())
                .onComplete(testContext.succeeding(readContent -> {
                    testContext.verify(() -> {
                        assertTrue(readContent.contains(content));
                        testContext.completeNow();
                    });
                }));
    }
}
