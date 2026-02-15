package me.stream.ganglia.core.llm;

import io.vertx.core.Vertx;
import me.stream.ganglia.core.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory to create and configure ModelGateways based on configuration.
 */
public class ModelGatewayFactory {
    private static final Logger logger = LoggerFactory.getLogger(ModelGatewayFactory.class);

    public static ModelGateway create(Vertx vertx, ConfigManager configManager) {
        String provider = configManager.getProvider();
        String apiKey = getApiKey(provider);

        if (apiKey == null || apiKey.isEmpty()) {
            logger.warn("No API key found for provider: {}. Ensure {}_API_KEY or LLM_API_KEY is set.", 
                provider, provider.toUpperCase());
        }

        return switch (provider.toLowerCase()) {
            case "anthropic" -> createAnthropic(vertx, configManager, apiKey);
            case "gemini" -> createGemini(vertx, apiKey);
            default -> createOpenAI(vertx, configManager, apiKey);
        };
    }

    private static String getApiKey(String provider) {
        // 1. Try platform-specific key: e.g. OPENAI_API_KEY
        String key = System.getenv(provider.toUpperCase() + "_API_KEY");
        
        // 2. Special case for Moonshot (commonly used in China)
        if (key == null && "openai".equalsIgnoreCase(provider)) {
            key = System.getenv("MOONSHOT_API_KEY");
        }

        // 3. Fallback to a UNIFIED key if set
        if (key == null || key.isEmpty()) {
            key = System.getenv("LLM_API_KEY");
            if (key != null && !key.isEmpty()) {
                logger.info("Using unified LLM_API_KEY for provider: {}", provider);
            }
        }

        return key;
    }

    private static ModelGateway createOpenAI(Vertx vertx, ConfigManager config, String apiKey) {
        String baseUrl = System.getenv("OPENAI_BASE_URL");
        if (baseUrl == null) baseUrl = config.getBaseUrl();
        logger.info("Initializing OpenAI provider (Base URL: {})", baseUrl);
        return new OpenAIModelGateway(vertx, apiKey, baseUrl);
    }

    private static ModelGateway createAnthropic(Vertx vertx, ConfigManager config, String apiKey) {
        String baseUrl = System.getenv("ANTHROPIC_BASE_URL");
        if (baseUrl == null) baseUrl = config.getAnthropicBaseUrl();
        
        com.anthropic.client.AnthropicClientAsync client = com.anthropic.client.okhttp.AnthropicOkHttpClientAsync.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build();
        
        logger.info("Initializing Anthropic provider (Base URL: {})", baseUrl);
        return new AnthropicModelGateway(vertx, client);
    }

    private static ModelGateway createGemini(Vertx vertx, String apiKey) {
        com.google.genai.Client geminiClient = com.google.genai.Client.builder()
                .apiKey(apiKey)
                .build();
        logger.info("Initializing Gemini provider");
        return new GeminiModelGateway(vertx, geminiClient);
    }
}
