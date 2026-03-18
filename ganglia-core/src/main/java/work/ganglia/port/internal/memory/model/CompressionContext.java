package work.ganglia.port.internal.memory.model;

/**
 * Context for tool output compression.
 */
public record CompressionContext(
    String toolName,
    String currentTaskDescription, // Task goal to guide LLM extraction
    int maxTokens                  // Desired maximum tokens after compression
) {}