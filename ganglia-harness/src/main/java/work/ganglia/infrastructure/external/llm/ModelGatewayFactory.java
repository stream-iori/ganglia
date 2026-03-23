package work.ganglia.infrastructure.external.llm;

import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.ganglia.config.ModelConfigProvider;
import work.ganglia.config.model.ModelConfig;
import work.ganglia.port.external.llm.ModelGateway;

/** Factory to create and configure ModelGateways based on configuration. */
public class ModelGatewayFactory {
  private static final Logger logger = LoggerFactory.getLogger(ModelGatewayFactory.class);

  public static ModelGateway create(Vertx vertx, ModelConfigProvider configProvider) {
    WebClient webClient = WebClient.create(vertx);
    ModelConfig primaryConfig = configProvider.getModelConfig("primary");
    if (primaryConfig == null) {
      logger.error("No 'primary' model configuration found.");
      throw new RuntimeException("Missing primary model configuration");
    }

    ModelGateway primaryGateway = createProvider(vertx, webClient, primaryConfig);

    ModelConfig utilityConfig = configProvider.getModelConfig("utility");
    if (utilityConfig != null) {
      ModelGateway utilityGateway = createProvider(vertx, webClient, utilityConfig);
      logger.info("Initializing fallback mechanism: Primary -> Utility ({})", utilityConfig.name());
      return new FallbackModelGateway(primaryGateway, utilityGateway, utilityConfig.name());
    }

    return primaryGateway;
  }

  private static ModelGateway createProvider(Vertx vertx, WebClient webClient, ModelConfig config) {
    String type = config.type();
    String apiKey = config.apiKey();

    if (apiKey == null || apiKey.isEmpty()) {
      logger.warn(
          "No API key found in configuration for provider: {} (Model: {}).", type, config.name());
    }

    ModelGateway baseGateway =
        switch (type.toLowerCase()) {
          case "anthropic" -> createAnthropic(vertx, webClient, config);
          case "gemini" -> createGemini(vertx, webClient, config);
          default -> createOpenAI(vertx, webClient, config);
        };

    int maxRetries = config.getMaxRetriesOrDefault();
    if (maxRetries > 0) {
      logger.debug(
          "Wrapping provider {} with RetryingModelGateway (maxRetries: {})", type, maxRetries);
      return new RetryingModelGateway(baseGateway, vertx, maxRetries);
    }

    return baseGateway;
  }

  private static ModelGateway createOpenAI(Vertx vertx, WebClient webClient, ModelConfig config) {
    String baseUrl = config.baseUrl();
    if (baseUrl == null || baseUrl.isEmpty()) {
      baseUrl = "https://api.openai.com/v1";
    }
    logger.info("Initializing OpenAI provider (Base URL: {})", baseUrl);
    GatewayConfig gatewayConfig =
        new GatewayConfig(config.apiKey(), baseUrl, config.getTimeoutOrDefault());
    return new OpenAIModelGateway(vertx, webClient, gatewayConfig);
  }

  private static ModelGateway createAnthropic(
      Vertx vertx, WebClient webClient, ModelConfig config) {
    String baseUrl = config.baseUrl();
    if (baseUrl == null || baseUrl.isEmpty()) {
      baseUrl = "https://api.anthropic.com";
    }
    logger.info("Initializing Anthropic provider (Base URL: {})", baseUrl);
    GatewayConfig gatewayConfig =
        new GatewayConfig(config.apiKey(), baseUrl, config.getTimeoutOrDefault());
    return new AnthropicModelGateway(vertx, webClient, gatewayConfig);
  }

  private static ModelGateway createGemini(Vertx vertx, WebClient webClient, ModelConfig config) {
    String baseUrl = config.baseUrl();
    if (baseUrl == null || baseUrl.isEmpty()) {
      baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai";
    }
    logger.info("Initializing Gemini provider via OpenAI compatibility (Base URL: {})", baseUrl);
    GatewayConfig gatewayConfig =
        new GatewayConfig(config.apiKey(), baseUrl, config.getTimeoutOrDefault());
    return new OpenAIModelGateway(vertx, webClient, gatewayConfig);
  }
}
