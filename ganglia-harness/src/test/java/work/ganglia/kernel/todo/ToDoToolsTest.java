package work.ganglia.kernel.todo;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.internal.memory.ContextCompressor;
import work.ganglia.port.internal.state.ExecutionContext;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
class ToDoToolsTest {

  private ToDoTools tools;
  private SessionContext context;
  private ExecutionContext execContext;
  @Mock private ContextCompressor compressor;

  @BeforeEach
  void setUp(Vertx vertx) {
    tools = new ToDoTools(vertx, compressor);
    String sessionId = UUID.randomUUID().toString();
    context =
        new SessionContext(
            sessionId,
            Collections.emptyList(),
            null,
            Map.of("todo_list", ToDoList.empty()),
            Collections.emptyList(),
            null);
    execContext =
        new ExecutionContext() {
          @Override
          public String sessionId() {
            return sessionId;
          }

          @Override
          public void emitStream(String chunk) {}

          @Override
          public void emitError(Throwable error) {}
        };
  }

  @Test
  void testAddAndList(VertxTestContext testContext) {
    Map<String, Object> args = new HashMap<>();
    args.put("description", "Task 1");

    tools
        .add(args, context, execContext)
        .compose(
            result -> {
              assertNotNull(result.modifiedContext());
              SessionContext ctx1 = result.modifiedContext();
              assertEquals(1, ((ToDoList) ctx1.metadata().get("todo_list")).items().size());
              return tools.list(ctx1);
            })
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertTrue(result.output().contains("Task 1"));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testComplete(VertxTestContext testContext) {
    when(compressor.summarize(any(), any()))
        .thenReturn(io.vertx.core.Future.succeededFuture("Task done."));

    Map<String, Object> addArgs = new HashMap<>();
    addArgs.put("description", "Task To Complete");

    tools
        .add(addArgs, context, execContext)
        .compose(
            result -> {
              SessionContext ctx1 = result.modifiedContext();
              String id = ((ToDoList) ctx1.metadata().get("todo_list")).items().get(0).id();
              Map<String, Object> completeArgs = new HashMap<>();
              completeArgs.put("id", id);
              return tools.complete(completeArgs, ctx1, execContext);
            })
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertNotNull(result.modifiedContext());
                        assertEquals(
                            TaskStatus.DONE,
                            ((ToDoList) result.modifiedContext().metadata().get("todo_list"))
                                .items()
                                .get(0)
                                .status());
                        testContext.completeNow();
                      });
                }));
  }
}
