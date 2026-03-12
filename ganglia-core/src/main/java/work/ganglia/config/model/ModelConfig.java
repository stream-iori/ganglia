package work.ganglia.config.model;

/**
 * Configuration for a specific model usage (e.g. primary, utility).
 */
public record ModelConfig(
    String name,
    double temperature,
    int maxTokens,
    int contextLimit, // Added to track total context window size
    String type,      // "openai", "anthropic", "gemini"
    String apiKey,
    String baseUrl,
    Boolean stream,
    Integer timeout,
    Integer maxRetries
) {
    public int getTimeoutOrDefault() {
        return timeout != null ? timeout : 60000;
    }

    public int getMaxRetriesOrDefault() {
        return maxRetries != null ? maxRetries : 5;
    }
}
