package work.ganglia.core.model;

public record ModelOptions(
    double temperature,
    int maxTokens,
    String modelName
) {}
