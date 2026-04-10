package work.ganglia.kernel.subagent;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class TaskNodeTest {

  @Test
  void fullConstructor_allFieldsSet() {
    TaskNode node =
        new TaskNode(
            "n1",
            "Investigate issue",
            "INVESTIGATOR",
            List.of("n0"),
            Map.of("context", "n0"),
            "Fix the login bug",
            ExecutionMode.DELEGATE,
            IsolationLevel.WORKTREE);

    assertEquals("n1", node.id());
    assertEquals("Investigate issue", node.task());
    assertEquals("INVESTIGATOR", node.persona());
    assertEquals(List.of("n0"), node.dependencies());
    assertEquals(Map.of("context", "n0"), node.inputMapping());
    assertEquals("Fix the login bug", node.missionContext());
    assertEquals(ExecutionMode.DELEGATE, node.mode());
    assertEquals(IsolationLevel.WORKTREE, node.isolation());
  }

  @Test
  void compactConstructor_defaultsApplied() {
    TaskNode node = new TaskNode("n1", "Task", "GENERAL", List.of(), null);

    assertNull(node.missionContext());
    assertEquals(ExecutionMode.SELF, node.mode());
    assertEquals(IsolationLevel.NONE, node.isolation());
  }

  @Test
  void compactConstructor_backwardCompatible() {
    // Simulates existing code that creates TaskNode with 5 args
    TaskNode node = new TaskNode("n1", "Do something", "REFACTORER", List.of("n0"), null);

    assertEquals("n1", node.id());
    assertEquals("Do something", node.task());
    assertEquals("REFACTORER", node.persona());
    assertEquals(List.of("n0"), node.dependencies());
    assertNull(node.inputMapping());
    assertNull(node.missionContext());
    assertEquals(ExecutionMode.SELF, node.mode());
    assertEquals(IsolationLevel.NONE, node.isolation());
  }
}
