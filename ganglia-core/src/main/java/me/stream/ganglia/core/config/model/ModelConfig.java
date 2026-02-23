package me.stream.ganglia.core.config.model;

/**
 * Configuration for a specific model usage (e.g. primary, utility).
 */
public record ModelConfig(
    String name,
    double temperature,
    int maxTokens,
    String type,      // "openai", "anthropic", "gemini"
    String apiKey,
    String baseUrl
) {}
