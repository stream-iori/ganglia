package me.stream.ganglia.core.tools;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import me.stream.ganglia.memory.KnowledgeBase;
import me.stream.ganglia.core.model.SessionContext;
import me.stream.ganglia.tools.model.ToDoList;
import me.stream.ganglia.tools.KnowledgeBaseTools;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(VertxExtension.class)
class KnowledgeBaseToolsTest {

    private Vertx vertx;
    private KnowledgeBase knowledgeBase;
    private KnowledgeBaseTools tools;
    private static final String TEST_MEMORY_FILE = "TEST_MEMORY_TOOLS.md";

    @BeforeEach
    void setUp(Vertx vertx) {
        this.vertx = vertx;
        this.knowledgeBase = new KnowledgeBase(vertx, TEST_MEMORY_FILE);
        this.tools = new KnowledgeBaseTools(vertx, knowledgeBase);
    }

    @AfterEach
    void tearDown(Vertx vertx, VertxTestContext testContext) {
        vertx.fileSystem().delete(TEST_MEMORY_FILE)
                .onComplete(ar -> testContext.completeNow());
    }

    @Test
    void testRemember(VertxTestContext testContext) {
        SessionContext context = new SessionContext(UUID.randomUUID().toString(), Collections.emptyList(), null, Collections.emptyMap(), Collections.emptyList(), null, ToDoList.empty());
        String fact = "The sky is blue.";

        tools.remember(Map.of("fact", fact), context)
                .compose(result -> knowledgeBase.read())
                .onComplete(testContext.succeeding(content -> {
                    testContext.verify(() -> {
                        assertTrue(content.contains(fact));
                        testContext.completeNow();
                    });
                }));
    }
}
