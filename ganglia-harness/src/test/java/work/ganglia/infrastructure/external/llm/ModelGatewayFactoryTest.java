package work.ganglia.infrastructure.external.llm;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;

import work.ganglia.config.ModelConfigProvider;
import work.ganglia.config.model.ModelConfig;
import work.ganglia.port.external.llm.ModelGateway;

@ExtendWith(VertxExtension.class)
class ModelGatewayFactoryTest {

  private ModelConfig config(String type) {
    // maxRetries=0 to prevent wrapping in RetryingModelGateway
    return new ModelConfig("test-model", 0.7, 1024, 8192, type, "test-key", null, false, null, 0);
  }

  private ModelConfig configWithRetries(String type, int maxRetries) {
    return new ModelConfig(
        "test-model", 0.7, 1024, 8192, type, "test-key", null, false, null, maxRetries);
  }

  private ModelConfigProvider provider(ModelConfig primary, ModelConfig utility) {
    return new ModelConfigProvider() {
      @Override
      public ModelConfig getModelConfig(String modelKey) {
        return switch (modelKey) {
          case "primary" -> primary;
          case "utility" -> utility;
          default -> null;
        };
      }

      @Override
      public String getModel() {
        return primary != null ? primary.name() : "test";
      }

      @Override
      public String getUtilityModel() {
        return utility != null ? utility.name() : "";
      }

      @Override
      public double getTemperature() {
        return 0.7;
      }

      @Override
      public int getContextLimit() {
        return 8192;
      }

      @Override
      public int getMaxTokens() {
        return 1024;
      }

      @Override
      public boolean isStream() {
        return false;
      }

      @Override
      public boolean isUtilityStream() {
        return false;
      }

      @Override
      public String getBaseUrl() {
        return "http://localhost";
      }

      @Override
      public String getProvider() {
        return "openai";
      }
    };
  }

  @Test
  void testCreateOpenAIGateway(Vertx vertx) {
    ModelGateway gateway = ModelGatewayFactory.create(vertx, provider(config("openai"), null));
    assertNotNull(gateway);
    assertInstanceOf(OpenAIModelGateway.class, gateway);
  }

  @Test
  void testCreateAnthropicGateway(Vertx vertx) {
    ModelGateway gateway = ModelGatewayFactory.create(vertx, provider(config("anthropic"), null));
    assertNotNull(gateway);
    assertInstanceOf(AnthropicModelGateway.class, gateway);
  }

  @Test
  void testCreateGeminiGateway(Vertx vertx) {
    // Gemini uses OpenAI compatibility layer
    ModelGateway gateway = ModelGatewayFactory.create(vertx, provider(config("gemini"), null));
    assertNotNull(gateway);
    assertInstanceOf(OpenAIModelGateway.class, gateway);
  }

  @Test
  void testCreateUnknownTypeDefaultsToOpenAI(Vertx vertx) {
    ModelGateway gateway = ModelGatewayFactory.create(vertx, provider(config("unknown"), null));
    assertNotNull(gateway);
    assertInstanceOf(OpenAIModelGateway.class, gateway);
  }

  @Test
  void testCreateWithUtilityModelUsesFallback(Vertx vertx) {
    ModelGateway gateway =
        ModelGatewayFactory.create(vertx, provider(config("openai"), config("anthropic")));
    assertNotNull(gateway);
    assertInstanceOf(FallbackModelGateway.class, gateway);
  }

  @Test
  void testCreateWithMaxRetriesUsesRetrying(Vertx vertx) {
    ModelGateway gateway =
        ModelGatewayFactory.create(vertx, provider(configWithRetries("openai", 3), null));
    assertNotNull(gateway);
    assertInstanceOf(RetryingModelGateway.class, gateway);
  }

  @Test
  void testCreateWithMaxRetriesZeroNoRetrying(Vertx vertx) {
    ModelGateway gateway =
        ModelGatewayFactory.create(vertx, provider(configWithRetries("openai", 0), null));
    assertNotNull(gateway);
    assertInstanceOf(OpenAIModelGateway.class, gateway);
  }

  @Test
  void testMissingPrimaryConfigThrows(Vertx vertx) {
    assertThrows(
        RuntimeException.class, () -> ModelGatewayFactory.create(vertx, provider(null, null)));
  }

  @Test
  void testNullApiKeyLogsWarning(Vertx vertx) {
    // Config with null API key — should still create gateway (warning logged)
    ModelConfig noKey =
        new ModelConfig("model", 0.7, 1024, 8192, "openai", null, null, false, null, 0);
    ModelGateway gateway = ModelGatewayFactory.create(vertx, provider(noKey, null));
    assertNotNull(gateway);
  }

  @Test
  void testAnthropicWithCustomBaseUrl(Vertx vertx) {
    ModelConfig custom =
        new ModelConfig(
            "model", 0.7, 1024, 8192, "anthropic", "key", "http://custom:8080", false, null, 0);
    ModelGateway gateway = ModelGatewayFactory.create(vertx, provider(custom, null));
    assertInstanceOf(AnthropicModelGateway.class, gateway);
  }
}
