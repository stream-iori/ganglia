package work.ganglia.kernel.todo;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ToDoListTest {

  // ── construction ──────────────────────────────────────────────────────────────

  @Test
  void empty_createsEmptyList() {
    ToDoList list = ToDoList.empty();
    assertTrue(list.isEmpty());
    assertTrue(list.items().isEmpty());
  }

  @Test
  void constructor_nullItems_defaultsToEmpty() {
    ToDoList list = new ToDoList(null);
    assertNotNull(list.items());
    assertTrue(list.isEmpty());
  }

  // ── addTask ───────────────────────────────────────────────────────────────────

  @Test
  void addTask_incrementsCount() {
    ToDoList list = ToDoList.empty().addTask("First").addTask("Second");
    assertEquals(2, list.items().size());
  }

  @Test
  void addTask_assignsSequentialIds() {
    ToDoList list = ToDoList.empty().addTask("A").addTask("B").addTask("C");
    assertEquals("1", list.items().get(0).id());
    assertEquals("2", list.items().get(1).id());
    assertEquals("3", list.items().get(2).id());
  }

  @Test
  void addTask_defaultsToTodoStatus() {
    ToDoList list = ToDoList.empty().addTask("do something");
    assertEquals(TaskStatus.TODO, list.items().get(0).status());
  }

  // ── updateTaskStatus ─────────────────────────────────────────────────────────

  @Test
  void updateTaskStatus_changesStatus() {
    ToDoList list = ToDoList.empty().addTask("work").updateTaskStatus("1", TaskStatus.DONE);
    assertEquals(TaskStatus.DONE, list.items().get(0).status());
  }

  @Test
  void updateTaskStatus_notFound_throwsIllegalArgumentException() {
    ToDoList list = ToDoList.empty().addTask("task");
    assertThrows(
        IllegalArgumentException.class, () -> list.updateTaskStatus("99", TaskStatus.DONE));
  }

  @Test
  void updateTaskStatus_otherTasksUnchanged() {
    ToDoList list =
        ToDoList.empty().addTask("A").addTask("B").updateTaskStatus("1", TaskStatus.IN_PROGRESS);
    assertEquals(TaskStatus.IN_PROGRESS, list.items().get(0).status());
    assertEquals(TaskStatus.TODO, list.items().get(1).status());
  }

  // ── updateTaskResult ─────────────────────────────────────────────────────────

  @Test
  void updateTaskResult_setsResult() {
    ToDoList list = ToDoList.empty().addTask("analyze").updateTaskResult("1", "Found 3 issues");
    assertEquals("Found 3 issues", list.items().get(0).result());
  }

  @Test
  void updateTaskResult_unknownId_returnsUnchangedList() {
    ToDoList list = ToDoList.empty().addTask("task");
    ToDoList updated = list.updateTaskResult("99", "result");
    // silently ignored — no exception, original item unchanged
    assertEquals(1, updated.items().size());
    assertNull(updated.items().get(0).result());
  }

  // ── toString ─────────────────────────────────────────────────────────────────

  @Test
  void toString_emptyList_returnsNoTasks() {
    assertEquals("No tasks.", ToDoList.empty().toString());
  }

  @Test
  void toString_todoItem_showsEmptyBracket() {
    String s = ToDoList.empty().addTask("do X").toString();
    assertTrue(s.contains("[ ]"));
    assertTrue(s.contains("do X"));
  }

  @Test
  void toString_doneItem_showsXBracket() {
    String s = ToDoList.empty().addTask("do X").updateTaskStatus("1", TaskStatus.DONE).toString();
    assertTrue(s.contains("[x]"));
  }

  @Test
  void toString_itemWithResult_showsResult() {
    String s =
        ToDoList.empty()
            .addTask("analyze")
            .updateTaskStatus("1", TaskStatus.DONE)
            .updateTaskResult("1", "All good")
            .toString();
    assertTrue(s.contains("All good"), "Result should appear in toString output");
  }

  // ── toSummarizedString ───────────────────────────────────────────────────────

  @Test
  void toSummarizedString_emptyList_returnsNoTasks() {
    assertEquals("No tasks.", ToDoList.empty().toSummarizedString());
  }

  @Test
  void toSummarizedString_hideDoneShowsCount() {
    ToDoList list =
        ToDoList.empty().addTask("A").addTask("B").updateTaskStatus("1", TaskStatus.DONE);
    String s = list.toSummarizedString();
    assertTrue(s.contains("1/2 tasks completed"), "Should show done count");
    assertFalse(s.contains("A"), "Done task should be hidden");
    assertTrue(s.contains("B"), "Pending task should be shown");
  }

  @Test
  void toSummarizedString_inProgressShowsSlash() {
    ToDoList list =
        ToDoList.empty().addTask("active").updateTaskStatus("1", TaskStatus.IN_PROGRESS);
    String s = list.toSummarizedString();
    assertTrue(s.contains("[/]"), "IN_PROGRESS items should show [/]");
  }

  @Test
  void toSummarizedString_allDone_noPendingLines() {
    ToDoList list = ToDoList.empty().addTask("A").updateTaskStatus("1", TaskStatus.DONE);
    String s = list.toSummarizedString();
    // After summary header the pending section is empty — no task lines
    assertTrue(s.contains("1/1 tasks completed"));
    assertFalse(s.contains("[ ]") || s.contains("[/]"));
  }
}
