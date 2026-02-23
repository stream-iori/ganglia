package me.stream.ganglia.tools;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import me.stream.ganglia.core.model.SessionContext;
import me.stream.ganglia.tools.model.ToolCall;
import me.stream.ganglia.tools.model.ToDoList;
import me.stream.ganglia.tools.model.ToolInvokeResult;
import me.stream.ganglia.tools.BashTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(VertxExtension.class)
class BashToolsTest {

    private BashTools tools;
    private SessionContext context;

    @BeforeEach
    void setUp(Vertx vertx) {
        tools = new BashTools(vertx);
        context = new SessionContext(UUID.randomUUID().toString(), Collections.emptyList(), null, Collections.emptyMap(), Collections.emptyList(), null, ToDoList.empty());
    }

    @Test
    void testRunShellCommand(VertxTestContext testContext) {
        tools.execute(new ToolCall("id", "run_shell_command", Map.of("command", "echo 'hello world'")), context)
            .onComplete(testContext.succeeding(result -> {
                testContext.verify(() -> {
                    assertEquals(ToolInvokeResult.Status.SUCCESS, result.status());
                    assertTrue(result.output().contains("hello world"));
                    testContext.completeNow();
                });
            }));
    }
}
