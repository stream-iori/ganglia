package me.stream.ganglia.core.tools;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import me.stream.ganglia.core.model.SessionContext;
import me.stream.ganglia.core.model.ToDoList;
import me.stream.ganglia.core.tools.model.ToolInvokeResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(VertxExtension.class)
class SelectionToolsTest {

    private SelectionTools tools;
    private SessionContext context;

    @BeforeEach
    void setUp(Vertx vertx) {
        tools = new SelectionTools(vertx);
        context = new SessionContext(UUID.randomUUID().toString(), Collections.emptyList(), null, Collections.emptyMap(), Collections.emptyList(), null, ToDoList.empty());
    }

    @Test
    void testAskSelectionReturnsInterrupt(VertxTestContext testContext) {
        Map<String, Object> args = Map.of(
            "question", "Choose a language",
            "options", List.of("Java", "Python", "Go")
        );

        tools.askSelection(args, context)
                .onComplete(testContext.succeeding(result -> {
                    testContext.verify(() -> {
                        assertEquals(ToolInvokeResult.Status.INTERRUPT, result.status());
                        assertTrue(result.output().contains("1. Java"));
                        assertTrue(result.output().contains("2. Python"));
                        testContext.completeNow();
                    });
                }));
    }
}
