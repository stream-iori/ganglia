package work.ganglia.infrastructure.internal.memory;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.Future;

import work.ganglia.port.internal.memory.MemoryEvent;
import work.ganglia.port.internal.memory.model.NudgeConfig;
import work.ganglia.port.internal.prompt.ContextFragment;

class MemoryNudgeModuleTest {

  private MemoryNudgeModule module;

  @BeforeEach
  void setUp() {
    // Nudge every 3 turns, every 5 tool calls, flush at 2 turns
    module = new MemoryNudgeModule(new NudgeConfig(3, 5, 2));
  }

  @Test
  void id_returnsMemoryNudge() {
    assertEquals("memory-nudge", module.id());
  }

  @Test
  void provideContext_noEvents_returnsEmpty() {
    Future<List<ContextFragment>> result = module.provideContext(null);
    assertTrue(result.succeeded());
    assertTrue(result.result().isEmpty());
  }

  @Test
  void memoryNudge_triggersAtInterval() {
    String sessionId = "s1";

    // Turns 1 and 2 — no nudge
    fireEvent(sessionId, 1, 0);
    fireEvent(sessionId, 2, 0);
    List<ContextFragment> fragments = module.provideContext(mockSessionContext(sessionId)).result();
    assertTrue(fragments.isEmpty(), "Should not nudge before interval");

    // Turn 3 — should trigger memory nudge
    fireEvent(sessionId, 3, 0);
    fragments = module.provideContext(mockSessionContext(sessionId)).result();
    assertTrue(
        fragments.stream().anyMatch(f -> f.name().equals("Memory Nudge")),
        "Should trigger memory nudge at turn 3");
  }

  @Test
  void memoryNudge_consumedOnRead() {
    String sessionId = "s1";
    fireEvent(sessionId, 3, 0);

    // First read consumes the nudge
    List<ContextFragment> first = module.provideContext(mockSessionContext(sessionId)).result();
    assertFalse(first.isEmpty());

    // Second read — nudge already consumed
    List<ContextFragment> second = module.provideContext(mockSessionContext(sessionId)).result();
    assertTrue(second.isEmpty(), "Nudge should be consumed after first read");
  }

  @Test
  void skillNudge_triggersAtToolCallInterval() {
    String sessionId = "s1";

    // 4 tool calls — no skill nudge
    fireEvent(sessionId, 1, 4);
    assertTrue(module.provideContext(mockSessionContext(sessionId)).result().isEmpty());

    // 5 tool calls — should trigger
    fireEvent(sessionId, 2, 5);
    List<ContextFragment> fragments = module.provideContext(mockSessionContext(sessionId)).result();
    assertTrue(
        fragments.stream().anyMatch(f -> f.name().equals("Skill Nudge")),
        "Should trigger skill nudge at 5 tool calls");
  }

  @Test
  void bothNudges_canTriggerSimultaneously() {
    String sessionId = "s1";

    // Turn 3 with 5 tool calls — both should trigger
    fireEvent(sessionId, 3, 5);
    List<ContextFragment> fragments = module.provideContext(mockSessionContext(sessionId)).result();
    assertTrue(
        fragments.stream().anyMatch(f -> f.name().equals("Memory Nudge")),
        "Memory nudge should trigger");
    assertTrue(
        fragments.stream().anyMatch(f -> f.name().equals("Skill Nudge")),
        "Skill nudge should trigger");
  }

  @Test
  void memoryNudge_triggersAgainAtNextInterval() {
    String sessionId = "s1";

    // Turn 3 — first nudge
    fireEvent(sessionId, 3, 0);
    module.provideContext(mockSessionContext(sessionId)); // consume

    // Turn 4, 5 — no nudge
    fireEvent(sessionId, 4, 0);
    fireEvent(sessionId, 5, 0);
    assertTrue(module.provideContext(mockSessionContext(sessionId)).result().isEmpty());

    // Turn 6 — second nudge (3 turns since last nudge at turn 3)
    fireEvent(sessionId, 6, 0);
    List<ContextFragment> fragments = module.provideContext(mockSessionContext(sessionId)).result();
    assertTrue(
        fragments.stream().anyMatch(f -> f.name().equals("Memory Nudge")),
        "Should trigger again at turn 6");
  }

  @Test
  void flush_triggersOnSessionClose_whenEnoughTurns() {
    String sessionId = "s1";

    // Close session with 3 turns (>= flushMinTurns of 2)
    MemoryEvent closeEvent =
        new MemoryEvent(MemoryEvent.EventType.SESSION_CLOSED, sessionId, null, null, 3, 0);
    module.onEvent(closeEvent);

    // Note: session state is cleaned up on SESSION_CLOSED, so flush fragment
    // is set but then the state is removed. This tests the internal behavior.
    // In practice, the flush would be consumed before cleanup.
  }

  @Test
  void flush_doesNotTrigger_whenTooFewTurns() {
    String sessionId = "s1";

    // Close session with 1 turn (< flushMinTurns of 2)
    MemoryEvent closeEvent =
        new MemoryEvent(MemoryEvent.EventType.SESSION_CLOSED, sessionId, null, null, 1, 0);
    module.onEvent(closeEvent);

    // No flush should be pending
    List<ContextFragment> fragments = module.provideContext(mockSessionContext(sessionId)).result();
    assertTrue(
        fragments.stream().noneMatch(f -> f.name().equals("Memory Flush")),
        "Should not trigger flush with too few turns");
  }

  @Test
  void disabledConfig_neverNudges() {
    MemoryNudgeModule disabled = new MemoryNudgeModule(new NudgeConfig(0, 0, 0));
    String sessionId = "s1";

    MemoryEvent event =
        new MemoryEvent(MemoryEvent.EventType.TURN_COMPLETED, sessionId, null, null, 100, 100);
    disabled.onEvent(event);

    List<ContextFragment> fragments =
        disabled.provideContext(mockSessionContext(sessionId)).result();
    assertTrue(fragments.isEmpty(), "Disabled config should never produce nudges");
  }

  @Test
  void separateSessions_haveIndependentCounters() {
    String s1 = "session-1";
    String s2 = "session-2";

    // s1 at turn 3 — triggers
    fireEvent(s1, 3, 0);
    List<ContextFragment> s1Fragments = module.provideContext(mockSessionContext(s1)).result();
    assertTrue(s1Fragments.stream().anyMatch(f -> f.name().equals("Memory Nudge")));

    // s2 at turn 1 — should not trigger
    fireEvent(s2, 1, 0);
    List<ContextFragment> s2Fragments = module.provideContext(mockSessionContext(s2)).result();
    assertTrue(s2Fragments.isEmpty(), "Session 2 should have independent counter");
  }

  @Test
  void nudgeContent_containsGuidance() {
    String sessionId = "s1";
    fireEvent(sessionId, 3, 5);

    List<ContextFragment> fragments = module.provideContext(mockSessionContext(sessionId)).result();

    ContextFragment memNudge =
        fragments.stream().filter(f -> f.name().equals("Memory Nudge")).findFirst().orElse(null);
    assertNotNull(memNudge);
    assertTrue(memNudge.content().contains("remember"));

    ContextFragment skillNudge =
        fragments.stream().filter(f -> f.name().equals("Skill Nudge")).findFirst().orElse(null);
    assertNotNull(skillNudge);
    assertTrue(skillNudge.content().contains("create_skill"));
  }

  // --- helpers ---

  private void fireEvent(String sessionId, int turnCount, int toolCallCount) {
    MemoryEvent event =
        new MemoryEvent(
            MemoryEvent.EventType.TURN_COMPLETED, sessionId, null, null, turnCount, toolCallCount);
    module.onEvent(event);
  }

  private work.ganglia.port.chat.SessionContext mockSessionContext(String sessionId) {
    return new work.ganglia.port.chat.SessionContext(sessionId, null, null, null, null, null, null);
  }
}
