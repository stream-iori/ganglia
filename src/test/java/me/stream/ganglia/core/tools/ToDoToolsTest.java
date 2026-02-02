package me.stream.ganglia.core.tools;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import me.stream.ganglia.core.model.SessionContext;
import me.stream.ganglia.core.model.TaskStatus;
import me.stream.ganglia.core.model.ToDoList;
import me.stream.ganglia.core.tools.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
class ToDoToolsTest {

    private ToDoTools tools;
    private SessionContext context;

    @BeforeEach
    void setUp(Vertx vertx) {
        tools = new ToDoTools(vertx);
        context = new SessionContext(UUID.randomUUID().toString(), Collections.emptyList(), Collections.emptyMap(), Collections.emptyList(), null, ToDoList.empty());
    }

    @Test
    void testAddAndList(VertxTestContext testContext) {
        tools.add(Map.of("description", "Task 1"), context)
            .compose(result -> {
                assertNotNull(result.modifiedContext());
                SessionContext ctx1 = result.modifiedContext();
                assertEquals(1, ctx1.toDoList().items().size());
                return tools.list(ctx1);
            })
            .onComplete(testContext.succeeding(result -> {
                testContext.verify(() -> {
                    assertTrue(result.output().contains("Task 1"));
                    testContext.completeNow();
                });
            }));
    }

    @Test
    void testComplete(VertxTestContext testContext) {
        tools.add(Map.of("description", "Task To Complete"), context)
            .compose(result -> {
                SessionContext ctx1 = result.modifiedContext();
                String id = ctx1.toDoList().items().get(0).id();
                return tools.complete(Map.of("id", id), ctx1);
            })
            .onComplete(testContext.succeeding(result -> {
                testContext.verify(() -> {
                    assertNotNull(result.modifiedContext());
                    assertEquals(TaskStatus.DONE, result.modifiedContext().toDoList().items().get(0).status());
                    testContext.completeNow();
                });
            }));
    }
}
