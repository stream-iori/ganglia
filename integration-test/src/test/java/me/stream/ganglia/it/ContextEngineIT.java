package me.stream.ganglia.it;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import me.stream.ganglia.core.model.SessionContext;
import me.stream.ganglia.core.prompt.StandardPromptEngine;
import me.stream.ganglia.memory.KnowledgeBase;
import me.stream.ganglia.tools.model.ToDoList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(VertxExtension.class)
public class ContextEngineIT {

    private StandardPromptEngine promptEngine;
    private static final String GANGLIA_FILE = "GANGLIA.md";

    @BeforeEach
    void setUp(Vertx vertx) {
        promptEngine = new StandardPromptEngine(vertx, new KnowledgeBase(vertx), null, null);
    }

    @AfterEach
    void tearDown(Vertx vertx, VertxTestContext testContext) {
        vertx.fileSystem().exists(GANGLIA_FILE).onComplete(ar -> {
            if (ar.succeeded() && ar.result()) {
                vertx.fileSystem().delete(GANGLIA_FILE).onComplete(v -> testContext.completeNow());
            } else {
                testContext.completeNow();
            }
        });
    }

    @Test
    void testAgentAdaptsToGangliaFile(Vertx vertx, VertxTestContext testContext) {
        String mandates = "## [Mandates] (Priority: 2)\n- Never use system.out";
        
        vertx.fileSystem().writeFile(GANGLIA_FILE, Buffer.buffer(mandates))
            .compose(v -> {
                SessionContext context = new SessionContext(UUID.randomUUID().toString(), Collections.emptyList(), null, Collections.emptyMap(), Collections.emptyList(), null, ToDoList.empty());
                return promptEngine.buildSystemPrompt(context);
            })
            .onComplete(testContext.succeeding(prompt -> {
                testContext.verify(() -> {
                    assertTrue(prompt.contains("Mandates"), "Prompt should contain Mandates header");
                    assertTrue(prompt.contains("Never use system.out"), "Prompt should contain custom mandate");
                    testContext.completeNow();
                });
            }));
    }
}