package work.ganglia.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import work.ganglia.kernel.todo.TaskStatus;
import work.ganglia.kernel.todo.ToDoItem;
import work.ganglia.kernel.todo.ToDoList;

public class TaskPanelRendererTest {

  private ByteArrayOutputStream output;
  private PrintWriter writer;
  private TaskPanelRenderer renderer;

  @BeforeEach
  void setUp() {
    output = new ByteArrayOutputStream();
    writer = new PrintWriter(output, true, StandardCharsets.UTF_8);
    renderer = new TaskPanelRenderer();
  }

  @AfterEach
  void tearDown() {
    renderer.stopElapsedTimer();
  }

  @Test
  void testHeightWithNoTasks() {
    assertEquals(0, renderer.getHeight(24));
  }

  @Test
  void testHeightWithEmptyPlan() {
    renderer.updatePlan(ToDoList.empty());
    assertEquals(0, renderer.getHeight(24));
  }

  @Test
  void testHeightWithTasks() {
    ToDoList plan =
        new ToDoList(
            List.of(
                new ToDoItem("1", "Task one", TaskStatus.TODO),
                new ToDoItem("2", "Task two", TaskStatus.IN_PROGRESS)));
    renderer.updatePlan(plan);
    // 1 header + 2 items = 3
    assertEquals(3, renderer.getHeight(24));
  }

  @Test
  void testHeightCappedAtOneThirdTerminal() {
    List<ToDoItem> items = new java.util.ArrayList<>();
    for (int i = 1; i <= 20; i++) {
      items.add(new ToDoItem(String.valueOf(i), "Task " + i, TaskStatus.TODO));
    }
    renderer.updatePlan(new ToDoList(items));
    // 1 header + 20 items = 21, but capped at 24/3 = 8
    assertEquals(8, renderer.getHeight(24));
  }

  @Test
  void testRenderWithMixedStatuses() {
    ToDoList plan =
        new ToDoList(
            List.of(
                new ToDoItem("1", "Setup project", TaskStatus.DONE),
                new ToDoItem("2", "Implement feature", TaskStatus.IN_PROGRESS),
                new ToDoItem("3", "Write tests", TaskStatus.TODO)));
    renderer.updatePlan(plan);

    renderer.renderAt(writer, 10, 80, 4);
    writer.flush();

    String out = stripAnsi(output.toString(StandardCharsets.UTF_8));
    // Should contain done checkmark, in-progress square, todo empty square
    assertTrue(out.contains("\u2714"), "Should have checkmark for DONE task. Output: " + out);
    assertTrue(
        out.contains("\u25a0"), "Should have filled square for IN_PROGRESS task. Output: " + out);
    assertTrue(out.contains("\u25a1"), "Should have empty square for TODO task. Output: " + out);
  }

  @Test
  void testRenderHeaderShowsActiveTask() {
    ToDoList plan =
        new ToDoList(List.of(new ToDoItem("1", "Implement feature X", TaskStatus.IN_PROGRESS)));
    renderer.updatePlan(plan);

    renderer.renderAt(writer, 10, 80, 2);
    writer.flush();

    String out = stripAnsi(output.toString(StandardCharsets.UTF_8));
    assertTrue(
        out.contains("Implement feature X"),
        "Header should show active task description. Output: " + out);
  }

  @Test
  void testRenderWithNoActiveTaskShowsGenericHeader() {
    ToDoList plan = new ToDoList(List.of(new ToDoItem("1", "Done task", TaskStatus.DONE)));
    renderer.updatePlan(plan);

    renderer.renderAt(writer, 10, 80, 2);
    writer.flush();

    String out = stripAnsi(output.toString(StandardCharsets.UTF_8));
    assertTrue(
        out.contains("Tasks"),
        "Should show generic 'Tasks' header when no active task. Output: " + out);
  }

  @Test
  void testRenderEmptyPlanIsNoop() {
    renderer.updatePlan(ToDoList.empty());

    renderer.renderAt(writer, 10, 80, 0);
    writer.flush();

    String out = stripAnsi(output.toString(StandardCharsets.UTF_8));
    // Should be empty or contain no task-related content
    assertFalse(
        out.contains("\u2714"), "Should not render anything for empty plan. Output: " + out);
  }

  @Test
  void testUpdatePlanFromDataWithMap() {
    // Simulate EventBus deserialization: plan comes as a Map
    Map<String, Object> planMap =
        Map.of("items", List.of(Map.of("id", "1", "description", "Test task", "status", "TODO")));
    renderer.updatePlanFromData(planMap);

    assertNotNull(renderer.getCurrentPlan());
    assertEquals(1, renderer.getCurrentPlan().items().size());
    assertEquals("Test task", renderer.getCurrentPlan().items().get(0).description());
  }

  @Test
  void testUpdatePlanFromDataWithToDoList() {
    ToDoList plan = new ToDoList(List.of(new ToDoItem("1", "Direct plan", TaskStatus.TODO)));
    renderer.updatePlanFromData(plan);

    assertNotNull(renderer.getCurrentPlan());
    assertEquals("Direct plan", renderer.getCurrentPlan().items().get(0).description());
  }

  @Test
  void testOnTurnStarted() {
    renderer.onTurnStarted();
    // Just verify no exception; the elapsed time should start counting
  }

  @Test
  void testTreeConnectors() {
    ToDoList plan =
        new ToDoList(
            List.of(
                new ToDoItem("1", "First", TaskStatus.DONE),
                new ToDoItem("2", "Last", TaskStatus.TODO)));
    renderer.updatePlan(plan);

    renderer.renderAt(writer, 10, 80, 3);
    writer.flush();

    String out = stripAnsi(output.toString(StandardCharsets.UTF_8));
    assertTrue(
        out.contains("\u251c"), "Should have \u251c connector for non-last item. Output: " + out);
    assertTrue(
        out.contains("\u2514"), "Should have \u2514 connector for last item. Output: " + out);
  }

  /** Strips ANSI escape sequences so tests can assert on plain text content. */
  private static String stripAnsi(String s) {
    return s.replaceAll("\033\\[[0-9;]*[A-Za-z]", "");
  }
}
