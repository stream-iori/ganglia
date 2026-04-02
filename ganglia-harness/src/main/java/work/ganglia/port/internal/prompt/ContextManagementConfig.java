package work.ganglia.port.internal.prompt;

/**
 * Unified configuration for all context management operations.
 *
 * <p>This record bundles together all configuration records that were previously scattered across
 * multiple components:
 *
 * <ul>
 *   <li>{@link ContextBudget} - token budget allocation for context window
 *   <li>{@link CompressionBudget} - token budget for LLM-based compression calls
 *   <li>{@link MicrocompactConfig} - time-based microcompact settings
 *   <li>{@link SessionMemoryCompactConfig} - session memory based compression settings
 * </ul>
 *
 * @param budget token budget allocation derived from model's context window
 * @param compression token budget for LLM compression calls
 * @param microcompact time-based microcompact configuration
 * @param sessionMemory session memory based compression configuration
 */
public record ContextManagementConfig(
    ContextBudget budget,
    CompressionBudget compression,
    MicrocompactConfig microcompact,
    SessionMemoryCompactConfig sessionMemory) {

  /**
   * Creates a complete configuration from the model's context window parameters.
   *
   * <p>This is the primary factory method for production use, using default values for compression,
   * microcompact, and sessionMemory.
   *
   * @param contextLimit the model's context window size
   * @param maxGenerationTokens tokens reserved for model generation
   * @return a complete configuration with sensible defaults
   */
  public static ContextManagementConfig fromModel(int contextLimit, int maxGenerationTokens) {
    // For edge cases where contextLimit is very small or less than maxGenerationTokens,
    // use a simple budget that works correctly
    ContextBudget budget;
    if (contextLimit <= maxGenerationTokens || contextLimit < 4000) {
      // Use a minimal budget with sensible defaults for testing/edge cases
      budget =
          new ContextBudget(
              contextLimit, maxGenerationTokens, 500, 2000, 1400, 500, 1000, 200, contextLimit / 2);
    } else {
      budget = ContextBudget.from(contextLimit, maxGenerationTokens);
    }
    return new ContextManagementConfig(
        budget,
        CompressionBudget.defaults(),
        MicrocompactConfig.defaults(),
        SessionMemoryCompactConfig.defaults());
  }

  /**
   * Creates a complete configuration with custom compression settings.
   *
   * @param contextLimit the model's context window size
   * @param maxGenerationTokens tokens reserved for model generation
   * @param compression custom compression budget
   * @return a complete configuration
   */
  public static ContextManagementConfig fromModel(
      int contextLimit, int maxGenerationTokens, CompressionBudget compression) {
    return new ContextManagementConfig(
        ContextBudget.from(contextLimit, maxGenerationTokens),
        compression,
        MicrocompactConfig.defaults(),
        SessionMemoryCompactConfig.defaults());
  }

  /**
   * Creates a configuration with all custom settings.
   *
   * @param budget custom context budget
   * @param compression custom compression budget
   * @param microcompact custom microcompact config
   * @param sessionMemory custom session memory config
   * @return a complete configuration
   */
  public static ContextManagementConfig of(
      ContextBudget budget,
      CompressionBudget compression,
      MicrocompactConfig microcompact,
      SessionMemoryCompactConfig sessionMemory) {
    return new ContextManagementConfig(budget, compression, microcompact, sessionMemory);
  }

  // --- Convenience delegates to budget ---

  /** Returns the context window limit. */
  public int contextLimit() {
    return budget.contextLimit();
  }

  /** Returns the compression target token count. */
  public int compressionTarget() {
    return budget.compressionTarget();
  }

  /** Returns the max generation tokens. */
  public int maxGenerationTokens() {
    return budget.maxGenerationTokens();
  }

  /** Returns the tool output aggregate budget. */
  public int toolOutputAggregate() {
    return budget.toolOutputAggregate();
  }

  /** Returns the tool output per-message budget. */
  public int toolOutputPerMessage() {
    return budget.toolOutputPerMessage();
  }

  /** Returns a new config with updated budget. */
  public ContextManagementConfig withBudget(ContextBudget newBudget) {
    return new ContextManagementConfig(newBudget, compression, microcompact, sessionMemory);
  }

  /** Returns a new config with updated compression budget. */
  public ContextManagementConfig withCompression(CompressionBudget newCompression) {
    return new ContextManagementConfig(budget, newCompression, microcompact, sessionMemory);
  }

  /** Returns a new config with updated microcompact config. */
  public ContextManagementConfig withMicrocompact(MicrocompactConfig newMicrocompact) {
    return new ContextManagementConfig(budget, compression, newMicrocompact, sessionMemory);
  }

  /** Returns a new config with updated session memory config. */
  public ContextManagementConfig withSessionMemory(SessionMemoryCompactConfig newSessionMemory) {
    return new ContextManagementConfig(budget, compression, microcompact, newSessionMemory);
  }
}
