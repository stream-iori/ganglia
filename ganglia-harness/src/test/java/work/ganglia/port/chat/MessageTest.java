package work.ganglia.port.chat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.util.TokenCounter;

class MessageTest {

  private static final TokenCounter COUNTER = new TokenCounter();

  // ── factory methods ────────────────────────────────────────────────────────

  @Test
  void user_setsRoleAndContent() {
    Message m = Message.user("hello");
    assertEquals(Role.USER, m.role());
    assertEquals("hello", m.content());
    assertNotNull(m.id());
    assertTrue(m.toolCalls().isEmpty());
  }

  @Test
  void system_setsRoleSystem() {
    Message m = Message.system("sys prompt");
    assertEquals(Role.SYSTEM, m.role());
    assertEquals("sys prompt", m.content());
  }

  @Test
  void assistant_noTools_setsRoleAssistant() {
    Message m = Message.assistant("reply");
    assertEquals(Role.ASSISTANT, m.role());
    assertEquals("reply", m.content());
    assertTrue(m.toolCalls().isEmpty());
  }

  @Test
  void assistant_withTools_setsToolCalls() {
    ToolCall tc = new ToolCall("c1", "bash", Map.of("cmd", "ls"));
    Message m = Message.assistant("calling", List.of(tc));
    assertEquals(1, m.toolCalls().size());
    assertEquals("bash", m.toolCalls().get(0).toolName());
  }

  @Test
  void tool_twoArg_setsRoleTool() {
    Message m = Message.tool("call-1", "result output");
    assertEquals(Role.TOOL, m.role());
    assertEquals("result output", m.content());
    assertNotNull(m.toolObservation());
    assertEquals("call-1", m.toolObservation().toolCallId());
    assertNull(m.toolObservation().toolName());
  }

  @Test
  void tool_threeArg_setsToolName() {
    Message m = Message.tool("call-1", "bash", "output");
    assertEquals(Role.TOOL, m.role());
    assertEquals("bash", m.toolObservation().toolName());
    assertEquals("call-1", m.toolObservation().toolCallId());
  }

  // ── compact constructor null-guards ───────────────────────────────────────

  @Test
  void constructor_nullToolCalls_defaultsToEmptyList() {
    Message m = new Message("id", Role.USER, "content", null, null, null);
    assertNotNull(m.toolCalls());
    assertTrue(m.toolCalls().isEmpty());
  }

  @Test
  void constructor_nullTimestamp_defaultsToNow() {
    Message m = new Message("id", Role.USER, "content", null, null, null);
    assertNotNull(m.timestamp());
  }

  // ── countTokens ─────────────────────────────────────────────────────────────

  @Test
  void countTokens_contentOnly_countsContent() {
    Message m = Message.user("hello world");
    int tokens = m.countTokens(COUNTER);
    assertTrue(tokens > 0);
  }

  @Test
  void countTokens_withToolCalls_addsBothContentAndToolCallTokens() {
    ToolCall tc = new ToolCall("c1", "bash", Map.of("cmd", "echo hello"));
    Message m = Message.assistant("using tool", List.of(tc));

    int tokensWithTool = m.countTokens(COUNTER);
    Message noTool = Message.assistant("using tool");
    int tokensNoTool = noTool.countTokens(COUNTER);

    assertTrue(tokensWithTool > tokensNoTool, "Tool call tokens should increase count");
  }

  @Test
  void countTokens_nullContent_doesNotThrow() {
    Message m = new Message("id", Role.ASSISTANT, null, null, null, null);
    assertDoesNotThrow(() -> m.countTokens(COUNTER));
  }
}
