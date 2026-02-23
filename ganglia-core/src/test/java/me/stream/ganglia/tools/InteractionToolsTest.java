package me.stream.ganglia.tools;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import me.stream.ganglia.tools.InteractionTools;
import me.stream.ganglia.tools.model.ToolCall;
import me.stream.ganglia.tools.model.ToolInvokeResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
public class InteractionToolsTest {

    private InteractionTools tools;

    @BeforeEach
    void setUp(Vertx vertx) {
        tools = new InteractionTools(vertx);
    }

    @Test
    void testAskSelectionText(Vertx vertx, VertxTestContext testContext) {
        Map<String, Object> args = Map.of(
            "question", "Please provide more details about the bug.",
            "type", "text"
        );

        tools.execute(new ToolCall("id", "ask_selection", args), null)
            .onComplete(testContext.succeeding(res -> {
                testContext.verify(() -> {
                    assertEquals(ToolInvokeResult.Status.INTERRUPT, res.status());
                    assertTrue(res.output().contains("Please provide more details about the bug."));
                    testContext.completeNow();
                });
            }));
    }

    @Test
    void testAskSelectionChoice(Vertx vertx, VertxTestContext testContext) {
        Map<String, Object> args = Map.of(
            "question", "Which file should I delete?",
            "type", "choice",
            "options", List.of("file1.txt", "file2.txt")
        );

        tools.execute(new ToolCall("id", "ask_selection", args), null)
            .onComplete(testContext.succeeding(res -> {
                testContext.verify(() -> {
                    assertEquals(ToolInvokeResult.Status.INTERRUPT, res.status());
                    assertTrue(res.output().contains("Which file should I delete?"));
                    assertTrue(res.output().contains("1. file1.txt"));
                    assertTrue(res.output().contains("2. file2.txt"));
                    testContext.completeNow();
                });
            }));
    }
}
