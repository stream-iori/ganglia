package work.ganglia.infrastructure.internal.memory;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;

import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.internal.memory.LongTermMemory;
import work.ganglia.port.internal.memory.MemoryEvent;
import work.ganglia.port.internal.memory.MemoryModule;
import work.ganglia.port.internal.prompt.ContextFragment;

/**
 * Memory module that monitors knowledge base size and nudges the agent to consolidate when the
 * memory exceeds a threshold. Implements the "memory full → consolidate" pattern.
 */
public class MemoryConsolidationModule implements MemoryModule {
  private static final Logger logger = LoggerFactory.getLogger(MemoryConsolidationModule.class);

  private final LongTermMemory longTermMemory;
  private final int sizeThreshold;

  /** Whether a consolidation nudge is pending for each topic. */
  private volatile boolean projectConsolidationPending;

  private volatile boolean userConsolidationPending;

  /**
   * @param longTermMemory The long-term memory to monitor.
   * @param sizeThreshold Character count threshold that triggers consolidation nudge.
   */
  public MemoryConsolidationModule(LongTermMemory longTermMemory, int sizeThreshold) {
    this.longTermMemory = longTermMemory;
    this.sizeThreshold = sizeThreshold;
  }

  public MemoryConsolidationModule(LongTermMemory longTermMemory) {
    this(longTermMemory, 8000); // ~2000 tokens
  }

  @Override
  public String id() {
    return "memory-consolidation";
  }

  @Override
  public Future<List<ContextFragment>> provideContext(SessionContext context) {
    List<ContextFragment> fragments = new java.util.ArrayList<>();

    if (projectConsolidationPending) {
      projectConsolidationPending = false;
      fragments.add(
          ContextFragment.prunable(
              "Memory Consolidation Needed",
              CONSOLIDATION_PROMPT_PROJECT,
              ContextFragment.PRIORITY_MEMORY + 5));
    }

    if (userConsolidationPending) {
      userConsolidationPending = false;
      fragments.add(
          ContextFragment.prunable(
              "User Profile Consolidation Needed",
              CONSOLIDATION_PROMPT_USER,
              ContextFragment.PRIORITY_MEMORY + 5));
    }

    return Future.succeededFuture(fragments);
  }

  @Override
  public Future<Void> onEvent(MemoryEvent event) {
    if (event.type() == MemoryEvent.EventType.SESSION_CLOSED) {
      return checkSizes();
    }
    return Future.succeededFuture();
  }

  Future<Void> checkSizes() {
    return longTermMemory
        .getSize(LongTermMemory.DEFAULT_TOPIC)
        .compose(
            projectSize -> {
              if (projectSize > sizeThreshold) {
                logger.info(
                    "Project memory size {} exceeds threshold {}, nudging consolidation",
                    projectSize,
                    sizeThreshold);
                projectConsolidationPending = true;
              }
              return longTermMemory.getSize(LongTermMemory.USER_PROFILE_TOPIC);
            })
        .map(
            userSize -> {
              if (userSize > sizeThreshold) {
                logger.info(
                    "User profile size {} exceeds threshold {}, nudging consolidation",
                    userSize,
                    sizeThreshold);
                userConsolidationPending = true;
              }
              return (Void) null;
            })
        .recover(
            err -> {
              logger.debug("Could not check memory sizes: {}", err.getMessage());
              return Future.succeededFuture();
            });
  }

  private static final String CONSOLIDATION_PROMPT_PROJECT =
      """
      [System Nudge] The project knowledge base (MEMORY.md) is getting large. Consider consolidating:
      1. Read the current content with `recall_memory` or by reading MEMORY.md
      2. Remove duplicate or outdated entries
      3. Merge related items into concise summaries
      4. Use `remember(consolidated_content)` to save the cleaned-up version
      Keep only information that would be valuable in future sessions.""";

  private static final String CONSOLIDATION_PROMPT_USER =
      """
      [System Nudge] The user profile (USER.md) is getting large. Consider consolidating:
      1. Read the current user profile content
      2. Remove duplicate or contradictory preferences
      3. Merge related items into concise summaries
      4. Use `remember(consolidated_content, target="user")` to save the cleaned-up version""";
}
