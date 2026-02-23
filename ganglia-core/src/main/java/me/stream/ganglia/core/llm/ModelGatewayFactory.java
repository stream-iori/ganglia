package me.stream.ganglia.core.llm;

import io.vertx.core.Vertx;
import me.stream.ganglia.core.config.ConfigManager;
import me.stream.ganglia.core.config.model.ModelConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory to create and configure ModelGateways based on configuration.
 */
public class ModelGatewayFactory {
    private static final Logger logger = LoggerFactory.getLogger(ModelGatewayFactory.class);

    public static ModelGateway create(Vertx vertx, ConfigManager configManager) {
        ModelConfig primary = configManager.getGangliaConfig().getModel("primary");
        if (primary == null) {
            logger.error("No 'primary' model configuration found.");
            throw new RuntimeException("Missing primary model configuration");
        }

        String type = primary.type();
        String apiKey = primary.apiKey();

        if (apiKey == null || apiKey.isEmpty()) {
            logger.warn("No API key found in configuration for provider: {}.", type);
        }

        return switch (type.toLowerCase()) {
            case "anthropic" -> createAnthropic(vertx, primary);
            case "gemini" -> createGemini(vertx, primary);
            default -> createOpenAI(vertx, primary);
        };
    }

    private static ModelGateway createOpenAI(Vertx vertx, ModelConfig config) {
        String baseUrl = config.baseUrl();
        logger.info("Initializing OpenAI provider (Base URL: {})", baseUrl);
        return new OpenAIModelGateway(vertx, config.apiKey(), baseUrl);
    }

    private static ModelGateway createAnthropic(Vertx vertx, ModelConfig config) {
        String baseUrl = config.baseUrl();
        
        com.anthropic.client.AnthropicClientAsync client = com.anthropic.client.okhttp.AnthropicOkHttpClientAsync.builder()
                .apiKey(config.apiKey())
                .baseUrl(baseUrl)
                .build();
        
        logger.info("Initializing Anthropic provider (Base URL: {})", baseUrl);
        return new AnthropicModelGateway(vertx, client);
    }

    private static ModelGateway createGemini(Vertx vertx, ModelConfig config) {
        com.google.genai.Client geminiClient = com.google.genai.Client.builder()
                .apiKey(config.apiKey())
                .build();
        logger.info("Initializing Gemini provider");
        return new GeminiModelGateway(vertx, geminiClient);
    }
}
