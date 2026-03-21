package work.ganglia.port.external.llm;

public record ModelOptions(double temperature, int maxTokens, String modelName, boolean stream) {}
