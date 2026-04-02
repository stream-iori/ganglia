package work.ganglia.infrastructure.internal.state;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;

import work.ganglia.config.AgentConfigProvider;
import work.ganglia.config.ModelConfigProvider;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.internal.memory.ContextCompressor;
import work.ganglia.port.internal.prompt.CompressionBudget;
import work.ganglia.port.internal.prompt.ContextBudget;
import work.ganglia.port.internal.state.ContextOptimizer;
import work.ganglia.port.internal.state.FileRestorationService;
import work.ganglia.port.internal.state.ObservationDispatcher;
import work.ganglia.util.TokenCounter;

/**
 * Default implementation of ContextOptimizer using a pipeline of optimization steps.
 *
 * <p>This class has been refactored from a God Class into a thin orchestrator that:
 *
 * <ul>
 *   <li>Builds the optimization context with current token counts
 *   <li>Creates and executes the compression pipeline
 *   <li>Exposes slimOldToolResults for external time-based optimization
 * </ul>
 *
 * <p>The actual optimization logic is delegated to:
 *
 * <ul>
 *   <li>{@link HardLimitGuardStep} - Hard limit financial guardrail
 *   <li>{@link TimeBasedMicrocompactStep} - Time-based tool result clearing
 *   <li>{@link SlimmingStep} - Zero-cost context slimming
 *   <li>{@link CompressionStep} - LLM-based turn compression
 * </ul>
 */
public class DefaultContextOptimizer implements ContextOptimizer {
  private static final Logger logger = LoggerFactory.getLogger(DefaultContextOptimizer.class);

  private final ModelConfigProvider modelConfig;
  private final AgentConfigProvider agentConfig;
  private final TokenCounter tokenCounter;
  private final ObservationDispatcher dispatcher;
  private final ContextBudget budget;
  private final CompressionBudget compressionBudget;

  // Pipeline and steps
  private final ContextOptimizationPipeline pipeline;
  private final ContextSlimmer slimmer;
  private final TimeBasedMicrocompact microcompact;

  // Steps (kept for direct access if needed)
  private final HardLimitGuardStep hardLimitGuardStep;
  private final TimeBasedMicrocompactStep microcompactStep;
  private final SlimmingStep slimmingStep;
  private final CompressionStep compressionStep;

  public DefaultContextOptimizer(
      ModelConfigProvider modelConfig,
      AgentConfigProvider agentConfig,
      ContextCompressor compressor,
      TokenCounter tokenCounter) {
    this(modelConfig, agentConfig, compressor, tokenCounter, null, null, null, null);
  }

  public DefaultContextOptimizer(
      ModelConfigProvider modelConfig,
      AgentConfigProvider agentConfig,
      ContextCompressor compressor,
      TokenCounter tokenCounter,
      ObservationDispatcher dispatcher) {
    this(modelConfig, agentConfig, compressor, tokenCounter, dispatcher, null, null, null);
  }

  public DefaultContextOptimizer(
      ModelConfigProvider modelConfig,
      AgentConfigProvider agentConfig,
      ContextCompressor compressor,
      TokenCounter tokenCounter,
      ObservationDispatcher dispatcher,
      ContextBudget budget) {
    this(modelConfig, agentConfig, compressor, tokenCounter, dispatcher, budget, null, null);
  }

  public DefaultContextOptimizer(
      ModelConfigProvider modelConfig,
      AgentConfigProvider agentConfig,
      ContextCompressor compressor,
      TokenCounter tokenCounter,
      ObservationDispatcher dispatcher,
      ContextBudget budget,
      CompressionBudget compressionBudget) {
    this(
        modelConfig,
        agentConfig,
        compressor,
        tokenCounter,
        dispatcher,
        budget,
        compressionBudget,
        null);
  }

  public DefaultContextOptimizer(
      ModelConfigProvider modelConfig,
      AgentConfigProvider agentConfig,
      ContextCompressor compressor,
      TokenCounter tokenCounter,
      ObservationDispatcher dispatcher,
      ContextBudget budget,
      CompressionBudget compressionBudget,
      FileRestorationService fileRestorationService) {
    this.modelConfig = modelConfig;
    this.agentConfig = agentConfig;
    this.tokenCounter = tokenCounter;
    this.dispatcher = dispatcher;
    this.budget = budget;
    this.compressionBudget =
        compressionBudget != null ? compressionBudget : CompressionBudget.defaults();

    // Initialize helpers
    this.slimmer = new ContextSlimmer(tokenCounter);
    this.microcompact = new TimeBasedMicrocompact();

    // Create steps
    this.hardLimitGuardStep = new HardLimitGuardStep(dispatcher);
    this.microcompactStep =
        new TimeBasedMicrocompactStep(
            microcompact, agentConfig.getMicrocompactConfig(), tokenCounter);
    this.slimmingStep = new SlimmingStep(slimmer, tokenCounter);
    this.compressionStep =
        new CompressionStep(
            modelConfig,
            agentConfig,
            compressor,
            tokenCounter,
            dispatcher,
            budget,
            this.compressionBudget,
            fileRestorationService);

    // Build pipeline
    this.pipeline =
        ContextOptimizationPipeline.builder(tokenCounter)
            .systemOverheadTokens(agentConfig.getSystemOverheadTokens())
            .addStep(hardLimitGuardStep)
            .addStep(microcompactStep)
            .addStep(slimmingStep)
            .addStep(compressionStep)
            .build();
  }

  @Override
  public Future<SessionContext> optimizeIfNeeded(SessionContext context) {
    return optimizeIfNeeded(context, null);
  }

  @Override
  public Future<SessionContext> optimizeIfNeeded(SessionContext context, String parentSpanId) {
    int limit = modelConfig.getContextLimit();
    double threshold = agentConfig.getCompressionThreshold();
    int hardLimit = (int) (limit * agentConfig.getHardLimitMultiplier());
    int forceLimit = (int) (limit * agentConfig.getForceCompressionMultiplier());

    logger.debug("Optimization check: limit: {}, threshold: {}%", limit, (int) (threshold * 100));

    return pipeline.execute(context, limit, threshold, forceLimit, hardLimit);
  }

  /**
   * Performs time-based slimming of old tool results.
   *
   * <p>This method is exposed for use by ReActAgentLoop to clear old tool results when the prompt
   * cache has likely expired.
   *
   * @param context the session context
   * @param maxAgeMs maximum age in milliseconds before clearing tool results
   * @return context with old tool results replaced by placeholders
   */
  public SessionContext slimOldToolResults(SessionContext context, long maxAgeMs) {
    return slimmer.slimOldToolResults(context, maxAgeMs);
  }

  /**
   * Returns the underlying ContextSlimmer for direct access if needed.
   *
   * @return the context slimmer
   */
  public ContextSlimmer getSlimmer() {
    return slimmer;
  }

  /**
   * Returns the underlying TimeBasedMicrocompact for direct access if needed.
   *
   * @return the time-based microcompact
   */
  public TimeBasedMicrocompact getMicrocompact() {
    return microcompact;
  }
}
