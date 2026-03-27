package work.ganglia.port.chat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import work.ganglia.port.external.llm.ModelOptions;
import work.ganglia.util.TokenCounter;

class SessionContextTest {

  private static final TokenCounter COUNTER = new TokenCounter();

  /** Build a Turn with one USER message of approximately {@code approxTokens} tokens. */
  private static Turn turnWithTokens(String id, int approxTokens) {
    // "word " ≈ 1 CL100K token; repeat to hit desired size
    String content = "word ".repeat(approxTokens);
    return Turn.newTurn(id, Message.user(content));
  }

  // -------------------------------------------------------------------------
  // getPrunedHistory — continue behaviour
  // -------------------------------------------------------------------------

  /**
   * When an intermediate previous turn is too large to fit in the remaining budget, it should be
   * skipped (continue) and older turns that do fit should still be included.
   *
   * <p>History (oldest → newest): turn1 (small), turn2 (oversized), turn3 (small) Budget = 200
   * tokens. Expected result: turn1 + turn3 (current), turn2 skipped.
   */
  @Test
  void getPrunedHistory_skipsOversizedTurnAndKeepsOlderSmallTurns() {
    Turn turn1 = turnWithTokens("t1", 80); // fits
    Turn turn2 = turnWithTokens("t2", 300); // oversized — exceeds budget on its own
    Turn turn3 = turnWithTokens("t3", 80); // fits — this becomes currentTurn

    SessionContext ctx =
        new SessionContext(
            "sid",
            List.of(turn1, turn2),
            turn3,
            Collections.emptyMap(),
            Collections.emptyList(),
            null);

    List<Message> pruned = ctx.getPrunedHistory(200, COUNTER);

    // turn3 (current) always included
    assertTrue(
        pruned.stream().anyMatch(m -> m.content().equals(turn3.userMessage().content())),
        "Current turn must always be included");

    // turn1 is small and should fit after turn2 is skipped
    assertTrue(
        pruned.stream().anyMatch(m -> m.content().equals(turn1.userMessage().content())),
        "Small older turn must be included even when a newer turn is skipped");

    // turn2 is oversized and must be skipped
    assertFalse(
        pruned.stream().anyMatch(m -> m.content().equals(turn2.userMessage().content())),
        "Oversized turn must be skipped");
  }

  /** When ALL previous turns are oversized only the current turn should survive. */
  @Test
  void getPrunedHistory_allOversized_onlyCurrentTurnReturned() {
    Turn turn1 = turnWithTokens("t1", 300);
    Turn turn2 = turnWithTokens("t2", 300);
    Turn current = turnWithTokens("tc", 50);

    SessionContext ctx =
        new SessionContext(
            "sid",
            List.of(turn1, turn2),
            current,
            Collections.emptyMap(),
            Collections.emptyList(),
            null);

    List<Message> pruned = ctx.getPrunedHistory(200, COUNTER);

    assertTrue(
        pruned.stream().anyMatch(m -> m.content().equals(current.userMessage().content())),
        "Current turn must always be included");
    assertFalse(
        pruned.stream().anyMatch(m -> m.content().equals(turn1.userMessage().content())),
        "Oversized turn1 must be skipped");
    assertFalse(
        pruned.stream().anyMatch(m -> m.content().equals(turn2.userMessage().content())),
        "Oversized turn2 must be skipped");
  }

  /** Normal case: all previous turns fit — should all be included in chronological order. */
  @Test
  void getPrunedHistory_allFit_returnsCompleteHistory() {
    Turn turn1 = turnWithTokens("t1", 50);
    Turn turn2 = turnWithTokens("t2", 50);
    Turn current = turnWithTokens("tc", 50);

    SessionContext ctx =
        new SessionContext(
            "sid",
            List.of(turn1, turn2),
            current,
            Collections.emptyMap(),
            Collections.emptyList(),
            null);

    List<Message> pruned = ctx.getPrunedHistory(500, COUNTER);

    assertEquals(3, pruned.size(), "All three turns must be present");
    // Chronological order: turn1, turn2, current
    assertEquals(turn1.userMessage().content(), pruned.get(0).content());
    assertEquals(turn2.userMessage().content(), pruned.get(1).content());
    assertEquals(current.userMessage().content(), pruned.get(2).content());
  }

  /** Current turn is always fully included regardless of the token budget. */
  @Test
  void getPrunedHistory_currentTurnAlwaysIncluded_evenWhenOversized() {
    Turn oversizedCurrent = turnWithTokens("tc", 500);

    SessionContext ctx =
        new SessionContext(
            "sid",
            Collections.emptyList(),
            oversizedCurrent,
            Collections.emptyMap(),
            Collections.emptyList(),
            null);

    List<Message> pruned = ctx.getPrunedHistory(10, COUNTER); // tiny budget

    assertEquals(1, pruned.size(), "Current turn must still be returned despite tiny budget");
    assertEquals(oversizedCurrent.userMessage().content(), pruned.get(0).content());
  }

  // -------------------------------------------------------------------------
  // withNewMessage
  // -------------------------------------------------------------------------

  @Test
  void withNewMessage_userRole_startsNewTurn() {
    SessionContext ctx =
        new SessionContext(
            "sid",
            Collections.emptyList(),
            null,
            Collections.emptyMap(),
            Collections.emptyList(),
            null);

    SessionContext next = ctx.withNewMessage(Message.user("hello"));

    assertNotNull(next.currentTurn());
    assertEquals("hello", next.currentTurn().userMessage().content());
  }

  @Test
  void withNewMessage_assistantRole_addsStep() {
    SessionContext ctx =
        new SessionContext(
            "sid",
            Collections.emptyList(),
            null,
            Collections.emptyMap(),
            Collections.emptyList(),
            null);

    SessionContext next = ctx.withNewMessage(Message.assistant("thinking..."));

    assertNotNull(next.currentTurn());
    assertFalse(next.currentTurn().intermediateSteps().isEmpty());
  }

  // -------------------------------------------------------------------------
  // withModelOptions
  // -------------------------------------------------------------------------

  @Test
  void withModelOptions_replacesOptions() {
    SessionContext ctx =
        new SessionContext(
            "sid",
            Collections.emptyList(),
            null,
            Collections.emptyMap(),
            Collections.emptyList(),
            null);

    ModelOptions opts = new ModelOptions(0.5, 1024, "gpt-4", true);
    SessionContext next = ctx.withModelOptions(opts);

    assertEquals(opts, next.modelOptions());
    assertNull(ctx.modelOptions(), "Original must be unchanged");
  }

  // -------------------------------------------------------------------------
  // completeTurn with null currentTurn
  // -------------------------------------------------------------------------

  @Test
  void completeTurn_nullCurrentTurn_createsNewTurnWithResponse() {
    SessionContext ctx =
        new SessionContext(
            "sid",
            Collections.emptyList(),
            null,
            Collections.emptyMap(),
            Collections.emptyList(),
            null);

    SessionContext next = ctx.completeTurn(Message.assistant("final answer"));

    assertNotNull(next.currentTurn());
    assertNotNull(next.currentTurn().finalResponse());
    assertEquals("final answer", next.currentTurn().finalResponse().content());
  }
}
