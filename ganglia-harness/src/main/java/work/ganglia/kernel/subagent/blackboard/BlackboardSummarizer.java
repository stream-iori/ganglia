package work.ganglia.kernel.subagent.blackboard;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;

import work.ganglia.kernel.loop.AgentLoop;
import work.ganglia.kernel.loop.AgentLoopFactory;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ObservationType;
import work.ganglia.port.internal.state.Blackboard;
import work.ganglia.port.internal.state.Fact;
import work.ganglia.port.internal.state.ObservationDispatcher;

/**
 * Summarizes superseded facts from the Blackboard into a single "Lessons Learned" fact.
 *
 * <p>Invocation: called by {@link work.ganglia.kernel.subagent.CyclicManagerEngine} when {@link
 * work.ganglia.kernel.subagent.CycleAwareTrigger#shouldSummarize(int)} returns true.
 *
 * <p>Process:
 *
 * <ol>
 *   <li>Fetch all SUPERSEDED facts from Blackboard
 *   <li>Build a summary prompt with their content
 *   <li>Call LLM to generate "Lessons Learned" abstraction
 *   <li>Publish new ACTIVE fact with the summary
 *   <li>Mark old facts as ARCHIVED
 *   <li>Dispatch FACT_ARCHIVED events
 * </ol>
 */
public class BlackboardSummarizer {
  private static final Logger logger = LoggerFactory.getLogger(BlackboardSummarizer.class);

  private final Blackboard blackboard;
  private final AgentLoopFactory loopFactory;
  private final ObservationDispatcher dispatcher;
  private final String sessionId;
  private final int maxSupersededToSummarize;

  /**
   * @param blackboard the fact store to summarize
   * @param loopFactory factory for creating agent loops to run LLM summarization
   * @param dispatcher observation dispatcher for trace integration
   * @param sessionId current session ID for observations
   * @param maxSupersededToSummarize max number of superseded facts to include in one summary
   */
  public BlackboardSummarizer(
      Blackboard blackboard,
      AgentLoopFactory loopFactory,
      ObservationDispatcher dispatcher,
      String sessionId,
      int maxSupersededToSummarize) {
    this.blackboard = blackboard;
    this.loopFactory = loopFactory;
    this.dispatcher = dispatcher;
    this.sessionId = sessionId;
    this.maxSupersededToSummarize = maxSupersededToSummarize;
  }

  /**
   * Summarizes superseded facts into a single "Lessons Learned" fact.
   *
   * @param parentContext the parent session context
   * @return future that completes when summarization is done
   */
  public Future<SummarizationResult> summarize(SessionContext parentContext) {
    logger.info("Starting Blackboard summarization for session {}", sessionId);

    return blackboard
        .getActiveFacts()
        .compose(
            activeFacts -> {
              // Get superseded facts by filtering active facts (they should already be filtered)
              // We need to fetch superseded facts separately
              return getSupersededFacts();
            })
        .compose(
            supersededFacts -> {
              if (supersededFacts.isEmpty()) {
                logger.info("No superseded facts to summarize");
                return Future.succeededFuture(
                    new SummarizationResult(0, "No superseded facts to summarize"));
              }

              // Limit the number of facts to summarize
              List<Fact> factsToSummarize =
                  supersededFacts.stream().limit(maxSupersededToSummarize).toList();

              logger.info(
                  "Summarizing {} superseded facts (max: {})",
                  factsToSummarize.size(),
                  maxSupersededToSummarize);

              return summarizeFacts(factsToSummarize, parentContext);
            });
  }

  private Future<List<Fact>> getSupersededFacts() {
    return blackboard.getSupersededFacts();
  }

  private Future<SummarizationResult> summarizeFacts(
      List<Fact> facts, SessionContext parentContext) {

    // Build prompt for LLM summarization
    StringBuilder promptBuilder = new StringBuilder();
    promptBuilder.append(
        "You are an expert analyst. Your task is to summarize the findings from a multi-cycle");
    promptBuilder.append(" agent execution into key \"Lessons Learned\".\n\n");
    promptBuilder.append("Extract the key insights, what worked, what didn't, and any patterns.\n");
    promptBuilder.append("Be concise but actionable.\n\n");
    promptBuilder.append("PREVIOUS FINDINGS:\n");

    for (Fact fact : facts) {
      promptBuilder.append("- ").append(fact.summary()).append("\n");
    }

    promptBuilder.append("\n\nGenerate a \"Lessons Learned\" summary (max 3 sentences):");

    String childSessionId = sessionId + "-summarizer";
    Map<String, Object> childMetadata = new HashMap<>();
    childMetadata.put("is_summarizer", true);
    SessionContext childContext =
        work.ganglia.kernel.subagent.ContextScoper.scope(
            childSessionId, parentContext, childMetadata);

    AgentLoop summarizerLoop = loopFactory.createLoop();

    return summarizerLoop
        .run(promptBuilder.toString(), childContext)
        .compose(
            summary -> {
              logger.info("Summarization complete: {}", summary);

              // Publish the summary as a new ACTIVE fact
              return blackboard
                  .publish("SUMMARIZER", "Lessons Learned: " + summary, null, 0)
                  .compose(
                      summaryFact -> {
                        // Mark old facts as ARCHIVED
                        return archiveFacts(facts)
                            .map(
                                archivedCount -> {
                                  dispatchArchiveEvents(facts);
                                  logger.info(
                                      "Archived {} facts, published summary fact {}",
                                      archivedCount,
                                      summaryFact.id());
                                  return new SummarizationResult(
                                      archivedCount,
                                      "Summarized "
                                          + archivedCount
                                          + " facts into lessons learned");
                                });
                      });
            });
  }

  private Future<Integer> archiveFacts(List<Fact> facts) {
    // Archive facts sequentially
    return archiveFactsSequential(facts, 0);
  }

  private Future<Integer> archiveFactsSequential(List<Fact> facts, int index) {
    if (index >= facts.size()) {
      return Future.succeededFuture(facts.size());
    }

    Fact fact = facts.get(index);
    return blackboard
        .archive(fact.id())
        .compose(v -> archiveFactsSequential(facts, index + 1))
        .recover(
            err -> {
              logger.warn("Failed to archive fact {}: {}", fact.id(), err.getMessage());
              return archiveFactsSequential(facts, index + 1);
            });
  }

  private void dispatchArchiveEvents(List<Fact> facts) {
    for (Fact fact : facts) {
      dispatcher.dispatch(
          sessionId,
          ObservationType.FACT_ARCHIVED,
          "Fact archived: " + fact.id(),
          Map.of("factId", fact.id(), "summary", fact.summary()));
    }
  }

  /** Result of summarization. */
  public record SummarizationResult(int archivedCount, String summaryMessage) {}
}
