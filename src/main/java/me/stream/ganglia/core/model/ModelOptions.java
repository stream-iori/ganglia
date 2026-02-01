package me.stream.ganglia.core.model;

public record ModelOptions(
    double temperature,
    int maxTokens,
    String modelName
) {}
