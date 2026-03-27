package work.ganglia.port.chat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import work.ganglia.port.external.tool.ToolCall;

class TurnTest {

  // ── getLatestMessage ────────────────────────────────────────────────────────

  @Test
  void getLatestMessage_withFinalResponse_returnsFinalResponse() {
    Turn turn =
        Turn.newTurn("t1", Message.user("hi"))
            .withStep(Message.assistant("thinking"))
            .withResponse(Message.assistant("done"));

    assertEquals("done", turn.getLatestMessage().content());
  }

  @Test
  void getLatestMessage_withStepButNoResponse_returnsLastStep() {
    Turn turn =
        Turn.newTurn("t1", Message.user("hi"))
            .withStep(Message.assistant("step1"))
            .withStep(Message.assistant("step2"));

    assertEquals("step2", turn.getLatestMessage().content());
  }

  @Test
  void getLatestMessage_noStepsNoResponse_returnsUserMessage() {
    Turn turn = Turn.newTurn("t1", Message.user("hello"));

    assertEquals("hello", turn.getLatestMessage().content());
  }

  @Test
  void getLatestMessage_emptySteps_returnsUserMessage() {
    Turn turn = new Turn("t1", Message.user("x"), List.of(), null);
    assertEquals("x", turn.getLatestMessage().content());
  }

  // ── getPendingToolCalls ──────────────────────────────────────────────────────

  @Test
  void getPendingToolCalls_noSteps_returnsEmpty() {
    Turn turn = Turn.newTurn("t1", Message.user("hi"));
    assertTrue(turn.getPendingToolCalls().isEmpty());
  }

  @Test
  void getPendingToolCalls_allAnswered_returnsEmpty() {
    ToolCall tc = new ToolCall("c1", "bash", Map.of());
    Message assistantMsg = Message.assistant("calling tool", List.of(tc));
    Message toolResult = Message.tool("c1", "bash", "output");

    Turn turn = Turn.newTurn("t1", Message.user("go")).withStep(assistantMsg).withStep(toolResult);

    assertTrue(turn.getPendingToolCalls().isEmpty());
  }

  @Test
  void getPendingToolCalls_unanswered_returnsPending() {
    ToolCall tc1 = new ToolCall("c1", "bash", Map.of());
    ToolCall tc2 = new ToolCall("c2", "read", Map.of());
    Message assistantMsg = Message.assistant("calling", List.of(tc1, tc2));
    // Only tc1 answered
    Message toolResult = Message.tool("c1", "bash", "output");

    Turn turn = Turn.newTurn("t1", Message.user("go")).withStep(assistantMsg).withStep(toolResult);

    List<ToolCall> pending = turn.getPendingToolCalls();
    assertEquals(1, pending.size());
    assertEquals("c2", pending.get(0).id());
  }

  @Test
  void getPendingToolCalls_interruptedObservation_treatedAsUnanswered() {
    ToolCall tc = new ToolCall("c1", "bash", Map.of());
    Message assistantMsg = Message.assistant("call", List.of(tc));
    // INTERRUPTED: prefix means it's a placeholder, NOT a real answer
    Message interrupted = Message.tool("c1", "bash", "INTERRUPTED: stopped");

    Turn turn = Turn.newTurn("t1", Message.user("go")).withStep(assistantMsg).withStep(interrupted);

    List<ToolCall> pending = turn.getPendingToolCalls();
    assertEquals(1, pending.size(), "INTERRUPTED: observation should not count as answered");
  }

  // ── getIterationCount ────────────────────────────────────────────────────────

  @Test
  void getIterationCount_noAssistantSteps_returnsZero() {
    Turn turn = Turn.newTurn("t1", Message.user("hi"));
    assertEquals(0, turn.getIterationCount());
  }

  @Test
  void getIterationCount_twoAssistantSteps_returnsTwo() {
    Turn turn =
        Turn.newTurn("t1", Message.user("hi"))
            .withStep(Message.assistant("step1"))
            .withStep(Message.tool("c1", "out"))
            .withStep(Message.assistant("step2"));

    assertEquals(2, turn.getIterationCount());
  }

  // ── compact constructor ───────────────────────────────────────────────────────

  @Test
  void constructor_nullIntermediateSteps_defaultsToEmpty() {
    Turn turn = new Turn("t1", Message.user("x"), null, null);
    assertNotNull(turn.intermediateSteps());
    assertTrue(turn.intermediateSteps().isEmpty());
  }

  // ── flatten ───────────────────────────────────────────────────────────────────

  @Test
  void flatten_includesAllMessagesInOrder() {
    Turn turn =
        Turn.newTurn("t1", Message.user("u"))
            .withStep(Message.assistant("a"))
            .withResponse(Message.assistant("final"));

    List<Message> flat = turn.flatten();
    assertEquals(3, flat.size());
    assertEquals(Role.USER, flat.get(0).role());
    assertEquals(Role.ASSISTANT, flat.get(1).role());
    assertEquals("final", flat.get(2).content());
  }
}
