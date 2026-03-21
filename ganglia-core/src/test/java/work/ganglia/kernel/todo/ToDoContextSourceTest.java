package work.ganglia.kernel.todo;

import static org.junit.jupiter.api.Assertions.*;

import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.internal.prompt.ContextFragment;

@ExtendWith(VertxExtension.class)
class ToDoContextSourceTest {

  @Test
  void testLayeredToDoFragments(VertxTestContext testContext) {
    ToDoContextSource source = new ToDoContextSource();

    ToDoList toDoList =
        ToDoList.empty()
            .addTask("Task 1")
            .addTask("Task 2")
            .updateTaskStatus("1", TaskStatus.DONE)
            .updateTaskResult("1", "Done with detail");

    SessionContext context =
        new SessionContext("test", List.of(), null, Map.of("todo_list", toDoList), List.of(), null);

    source
        .getFragments(context)
        .onComplete(
            ar -> {
              if (ar.failed()) {
                testContext.failNow(ar.cause());
                return;
              }

              List<ContextFragment> fragments = ar.result();
              assertEquals(2, fragments.size());

              // Check Mandatory Summary
              ContextFragment summary =
                  fragments.stream()
                      .filter(f -> f.name().contains("Summary"))
                      .findFirst()
                      .orElseThrow();
              assertTrue(summary.isMandatory());
              assertEquals(12, summary.priority());
              assertTrue(summary.content().contains("1/2 tasks completed"));
              assertFalse(summary.content().contains("Done with detail")); // Summarized

              // Check Prunable Details
              ContextFragment details =
                  fragments.stream()
                      .filter(f -> f.name().contains("Details"))
                      .findFirst()
                      .orElseThrow();
              assertFalse(details.isMandatory());
              assertEquals(49, details.priority());
              assertTrue(details.content().contains("Done with detail")); // Full details

              testContext.completeNow();
            });
  }

  @Test
  void testEmptyToDo(VertxTestContext testContext) {
    ToDoContextSource source = new ToDoContextSource();
    SessionContext context = new SessionContext("test", List.of(), null, Map.of(), List.of(), null);

    source
        .getFragments(context)
        .onComplete(
            ar -> {
              if (ar.failed()) {
                testContext.failNow(ar.cause());
                return;
              }

              List<ContextFragment> fragments = ar.result();
              assertEquals(1, fragments.size());
              assertTrue(fragments.get(0).isMandatory());
              assertTrue(fragments.get(0).content().contains("No active plan"));

              testContext.completeNow();
            });
  }

  @Test
  void testToDoListSummarization() {
    ToDoList toDoList =
        ToDoList.empty()
            .addTask("Finished Task")
            .addTask("Current Task")
            .updateTaskStatus("1", TaskStatus.DONE)
            .updateTaskResult("1", "Detailed Result")
            .updateTaskStatus("2", TaskStatus.IN_PROGRESS);

    String summarized = toDoList.toSummarizedString();
    String full = toDoList.toString();

    assertTrue(summarized.contains("1/2 tasks completed"));
    assertFalse(summarized.contains("Detailed Result"));
    assertTrue(summarized.contains("Current Task"));

    assertTrue(full.contains("Detailed Result"));
  }
}
