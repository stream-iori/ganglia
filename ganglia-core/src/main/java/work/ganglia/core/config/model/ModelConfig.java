package work.ganglia.core.config.model;

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
    String baseUrl
) {}
