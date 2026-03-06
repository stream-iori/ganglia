package work.ganglia.core.session;

import io.vertx.core.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.ganglia.core.config.ConfigManager;
import work.ganglia.core.model.Message;
import work.ganglia.core.model.SessionContext;
import work.ganglia.core.model.Turn;
import work.ganglia.memory.ContextCompressor;
import work.ganglia.memory.TokenCounter;

import java.util.ArrayList;
import java.util.List;

public class DefaultContextOptimizer implements ContextOptimizer {
    private static final Logger logger = LoggerFactory.getLogger(DefaultContextOptimizer.class);
    
    private final ConfigManager configManager;
    private final ContextCompressor compressor;
    private final TokenCounter tokenCounter;

    public DefaultContextOptimizer(ConfigManager configManager, ContextCompressor compressor, TokenCounter tokenCounter) {
        this.configManager = configManager;
        this.compressor = compressor;
        this.tokenCounter = tokenCounter;
    }

    @Override
    public Future<SessionContext> optimizeIfNeeded(SessionContext context) {
        int totalTokens = context.history().stream()
                .mapToInt(m -> m.countTokens(tokenCounter))
                .sum();

        int limit = configManager.getContextLimit();
        double threshold = configManager.getCompressionThreshold();

        // Hard limit financial guardrail
        if (totalTokens > 500000) {
            logger.error("Session token limit exceeded ({}). Aborting.", totalTokens);
            return Future.failedFuture("Session reached maximum safety token limit (500,000).");
        }

        if (totalTokens > limit * threshold && context.previousTurns().size() > 1) {
            logger.info("Context threshold reached ({} > {}). Triggering compression...", totalTokens, (int)(limit * threshold));
            return compressSession(context, 1).map(compressedContext -> {
                logger.info("Compression complete. New token count: {}",
                    compressedContext.history().stream().mapToInt(m -> m.countTokens(tokenCounter)).sum());
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

        return compressor.compress(toCompress)
            .map(summary -> {
                Message summaryMsg = Message.system("SUMMARY OF PREVIOUS INTERACTIONS:\n" + summary);
                Turn summaryTurn = Turn.newTurn("summary-" + System.currentTimeMillis(), summaryMsg);

                List<Turn> newPrevious = new ArrayList<>();
                newPrevious.add(summaryTurn);
                newPrevious.addAll(toKeep);

                return context.withPreviousTurns(newPrevious);
            });
    }
}