package work.ganglia.infrastructure.internal.memory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import io.vertx.core.Future;

import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.internal.memory.MemoryEvent;
import work.ganglia.port.internal.memory.MemoryModule;
import work.ganglia.port.internal.memory.model.NudgeConfig;
import work.ganglia.port.internal.prompt.ContextFragment;

/**
 * Memory module that proactively nudges the agent to save valuable memories and create skills at
 * appropriate intervals. Implements the Nudge + Flush pattern inspired by hermes-agent.
 */
public class MemoryNudgeModule implements MemoryModule {
  private final NudgeConfig config;

  /** Tracks per-session nudge state: which nudges have been triggered. */
  private final ConcurrentHashMap<String, NudgeState> sessionStates = new ConcurrentHashMap<>();

  private static class NudgeState {
    volatile boolean memoryNudgePending;
    volatile boolean skillNudgePending;
    volatile boolean flushPending;
    int lastMemoryNudgeTurn;
    int lastSkillNudgeToolCount;
  }

  public MemoryNudgeModule(NudgeConfig config) {
    this.config = config;
  }

  public MemoryNudgeModule() {
    this(NudgeConfig.DEFAULT);
  }

  @Override
  public String id() {
    return "memory-nudge";
  }

  @Override
  public Future<List<ContextFragment>> provideContext(SessionContext context) {
    if (context == null) {
      return Future.succeededFuture(List.of());
    }
    NudgeState state = sessionStates.get(context.sessionId());
    if (state == null) {
      return Future.succeededFuture(List.of());
    }

    List<ContextFragment> fragments = new ArrayList<>();

    if (state.memoryNudgePending) {
      state.memoryNudgePending = false;
      fragments.add(
          ContextFragment.prunable(
              "Memory Nudge", MEMORY_NUDGE_PROMPT, ContextFragment.PRIORITY_MEMORY + 10));
    }

    if (state.skillNudgePending) {
      state.skillNudgePending = false;
      fragments.add(
          ContextFragment.prunable(
              "Skill Nudge", SKILL_NUDGE_PROMPT, ContextFragment.PRIORITY_MEMORY + 10));
    }

    if (state.flushPending) {
      state.flushPending = false;
      fragments.add(
          ContextFragment.prunable(
              "Memory Flush", FLUSH_PROMPT, ContextFragment.PRIORITY_MEMORY + 10));
    }

    return Future.succeededFuture(fragments);
  }

  @Override
  public Future<Void> onEvent(MemoryEvent event) {
    String sessionId = event.sessionId();
    NudgeState state = sessionStates.computeIfAbsent(sessionId, k -> new NudgeState());

    switch (event.type()) {
      case TURN_COMPLETED -> {
        // Check memory nudge interval
        if (config.isMemoryNudgeEnabled()
            && event.turnCount() > 0
            && event.turnCount() - state.lastMemoryNudgeTurn >= config.memoryNudgeInterval()) {
          state.memoryNudgePending = true;
          state.lastMemoryNudgeTurn = event.turnCount();
        }

        // Check skill nudge interval
        if (config.isSkillNudgeEnabled()
            && event.toolCallCount() > 0
            && event.toolCallCount() - state.lastSkillNudgeToolCount
                >= config.skillNudgeInterval()) {
          state.skillNudgePending = true;
          state.lastSkillNudgeToolCount = event.toolCallCount();
        }
      }
      case SESSION_CLOSED -> {
        // Flush reminder if session had enough turns
        if (config.isFlushEnabled() && event.turnCount() >= config.flushMinTurns()) {
          state.flushPending = true;
        }
        // Cleanup after a delay to allow the flush fragment to be consumed
        sessionStates.remove(sessionId);
      }
      default -> {}
    }

    return Future.succeededFuture();
  }

  private static final String MEMORY_NUDGE_PROMPT =
      """
      [System Nudge] You have been working for several turns. Consider saving valuable information:
      - Use `remember(fact)` to save project knowledge (conventions, architecture decisions, lessons learned).
      - Use `remember(fact, target="user")` to save user preferences or communication style.
      Only save information that would be valuable in future sessions — skip trivial or easily re-discovered facts.""";

  private static final String SKILL_NUDGE_PROMPT =
      """
      [System Nudge] You have made many tool calls in this session. If you completed a complex \
      multi-step operation, consider saving the approach as a reusable Skill:
      - Use `create_skill(name, description, category, instructions)` to save the operation pattern.
      - Good skill candidates: deployment procedures, debugging workflows, code generation patterns.
      Only create a skill if the operation is likely to be repeated.""";

  private static final String FLUSH_PROMPT =
      """
      [System Nudge] This session is ending. Before it closes, consider saving any critical information \
      that would be lost:
      - Key decisions made during this session
      - Important patterns or conventions discovered
      - User preferences observed
      Use `remember(fact)` or `remember(fact, target="user")` to persist them.""";
}
