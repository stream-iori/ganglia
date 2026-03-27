package work.ganglia.kernel.todo;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ToDoItemTest {

  @Test
  void threeArgConstructor_setsNullResult() {
    ToDoItem item = new ToDoItem("1", "Do something", TaskStatus.TODO);
    assertEquals("1", item.id());
    assertEquals("Do something", item.description());
    assertEquals(TaskStatus.TODO, item.status());
    assertNull(item.result());
  }

  @Test
  void fourArgConstructor_preservesResult() {
    ToDoItem item = new ToDoItem("2", "Task", TaskStatus.DONE, "completed successfully");
    assertEquals("completed successfully", item.result());
  }

  @Test
  void withStatus_returnsNewItemWithUpdatedStatus() {
    ToDoItem original = new ToDoItem("1", "Task", TaskStatus.TODO);
    ToDoItem updated = original.withStatus(TaskStatus.IN_PROGRESS);

    assertEquals(TaskStatus.IN_PROGRESS, updated.status());
    // original unchanged
    assertEquals(TaskStatus.TODO, original.status());
    // other fields preserved
    assertEquals("1", updated.id());
    assertEquals("Task", updated.description());
    assertNull(updated.result());
  }

  @Test
  void withResult_returnsNewItemWithUpdatedResult() {
    ToDoItem original = new ToDoItem("1", "Task", TaskStatus.TODO);
    ToDoItem updated = original.withResult("done with flying colors");

    assertEquals("done with flying colors", updated.result());
    // original unchanged
    assertNull(original.result());
    // other fields preserved
    assertEquals("1", updated.id());
    assertEquals(TaskStatus.TODO, updated.status());
  }

  @Test
  void withStatus_chainedWithWithResult_preservesAllFields() {
    ToDoItem item =
        new ToDoItem("3", "Complex task", TaskStatus.TODO)
            .withStatus(TaskStatus.DONE)
            .withResult("success");

    assertEquals("3", item.id());
    assertEquals("Complex task", item.description());
    assertEquals(TaskStatus.DONE, item.status());
    assertEquals("success", item.result());
  }
}
