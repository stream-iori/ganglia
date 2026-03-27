package work.ganglia.infrastructure.internal.state;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;

import work.ganglia.config.AgentConfigProvider;
import work.ganglia.config.ModelConfigProvider;
import work.ganglia.port.chat.Message;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.chat.Turn;
import work.ganglia.port.external.tool.ObservationType;
import work.ganglia.port.internal.memory.ContextCompressor;
import work.ganglia.port.internal.state.ContextOptimizer;
import work.ganglia.port.internal.state.ObservationDispatcher;
import work.ganglia.util.TokenCounter;

public class DefaultContextOptimizer implements ContextOptimizer {
  private static final Logger logger = LoggerFactory.getLogger(DefaultContextOptimizer.class);

  private final ModelConfigProvider modelConfig;
  private final AgentConfigProvider agentConfig;
  private final ContextCompressor compressor;
  private final TokenCounter tokenCounter;
  private final ObservationDispatcher dispatcher;

  public DefaultContextOptimizer(
      ModelConfigProvider modelConfig,
      AgentConfigProvider agentConfig,
      ContextCompressor compressor,
      TokenCounter tokenCounter) {
    this(modelConfig, agentConfig, compressor, tokenCounter, null);
  }

  public DefaultContextOptimizer(
      ModelConfigProvider modelConfig,
      AgentConfigProvider agentConfig,
      ContextCompressor compressor,
      TokenCounter tokenCounter,
      ObservationDispatcher dispatcher) {
    this.modelConfig = modelConfig;
    this.agentConfig = agentConfig;
    this.compressor = compressor;
    this.tokenCounter = tokenCounter;
    this.dispatcher = dispatcher;
  }

  @Override
  public Future<SessionContext> optimizeIfNeeded(SessionContext context) {
    int totalTokens = context.history().stream().mapToInt(m -> m.countTokens(tokenCounter)).sum();

    int limit = modelConfig.getContextLimit();
    double threshold = agentConfig.getCompressionThreshold();

    // Hard limit financial guardrail
    if (totalTokens > 500000) {
      logger.error("Session token limit exceeded ({}). Aborting.", totalTokens);
      return Future.failedFuture("Session reached maximum safety token limit (500,000).");
    }

    if (totalTokens > limit * threshold && context.previousTurns().size() > 1) {
      logger.info(
          "Context threshold reached ({} > {}). Triggering compression...",
          totalTokens,
          (int) (limit * threshold));
      if (dispatcher != null) {
        Map<String, Object> startData = new java.util.HashMap<>();
        startData.put("beforeTokens", totalTokens);
        startData.put("contextLimit", limit);
        dispatcher.dispatch(
            context.sessionId(),
            ObservationType.CONTEXT_COMPRESSED,
            "context_compression_started",
            startData);
      }
      final int beforeTokens = totalTokens;
      return compressSession(context, 1)
          .map(
              compressedContext -> {
                int afterTokens =
                    compressedContext.history().stream()
                        .mapToInt(m -> m.countTokens(tokenCounter))
                        .sum();
                logger.info("Compression complete. New token count: {}", afterTokens);
                if (dispatcher != null) {
                  Map<String, Object> finishData = new java.util.HashMap<>();
                  finishData.put("beforeTokens", beforeTokens);
                  finishData.put("afterTokens", afterTokens);
                  dispatcher.dispatch(
                      compressedContext.sessionId(),
                      ObservationType.SYSTEM_EVENT,
                      "context_compression_finished",
                      finishData);
                }
                return compressedContext;
              });
    }

    return Future.succeededFuture(context);
  }

  private Future<SessionContext> compressSession(SessionContext context, int turnsToKeep) {
    List<Turn> allPrevious = context.previousTurns();
    if (allPrevious.size() <= turnsToKeep) {
      return Future.succeededFuture(context);
    }

    int compressCount = allPrevious.size() - turnsToKeep;
    List<Turn> toCompress = allPrevious.subList(0, compressCount);
    List<Turn> toKeep = new ArrayList<>(allPrevious.subList(compressCount, allPrevious.size()));

    return compressor
        .compress(toCompress)
        .map(
            summary -> {
              Message summaryMsg = Message.system("SUMMARY OF PREVIOUS INTERACTIONS:\n" + summary);
              Turn summaryTurn = Turn.newTurn("summary-" + System.currentTimeMillis(), summaryMsg);

              List<Turn> newPrevious = new ArrayList<>();
              newPrevious.add(summaryTurn);
              newPrevious.addAll(toKeep);

              return context.withPreviousTurns(newPrevious);
            });
  }
}
