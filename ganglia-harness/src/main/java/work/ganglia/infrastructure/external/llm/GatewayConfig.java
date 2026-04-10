package work.ganglia.infrastructure.external.llm;

/** Configuration for LLM gateway service. */
public record GatewayConfig(String apiKey, String baseUrl, int timeout) {}
