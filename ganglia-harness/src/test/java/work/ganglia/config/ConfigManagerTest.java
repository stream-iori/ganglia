package work.ganglia.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.config.model.ModelConfig;

@ExtendWith(VertxExtension.class)
class ConfigManagerTest {

  private static final String TEST_CONFIG_FILE = "target/test-config.json";

  @BeforeEach
  void setUp() throws IOException {
    // Ensure clean state
    Files.deleteIfExists(Paths.get(TEST_CONFIG_FILE));
  }

  @AfterEach
  void tearDown() throws IOException {
    Files.deleteIfExists(Paths.get(TEST_CONFIG_FILE));
  }

  @Test
  void testDefaultConfig(Vertx vertx, VertxTestContext testContext) {
    ConfigManager configManager = new ConfigManager(vertx, TEST_CONFIG_FILE);
    configManager
        .init()
        .onComplete(
            testContext.succeeding(
                v -> {
                  testContext.verify(
                      () -> {
                        assertEquals("kimi-k2-0905-preview", configManager.getModel());
                        assertEquals(0.0, configManager.getTemperature());
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testLoadFromFile(Vertx vertx, VertxTestContext testContext) throws IOException {
    // Create structured JSON
    JsonObject primaryModel =
        new JsonObject()
            .put("name", "custom-model")
            .put("temperature", 0.7)
            .put("maxTokens", 1000)
            .put("type", "openai")
            .put("apiKey", "test-key")
            .put("baseUrl", "http://test.url");

    JsonObject customConfig = new JsonObject().put("models", Map.of("primary", primaryModel));

    Files.write(Paths.get(TEST_CONFIG_FILE), customConfig.encodePrettily().getBytes());

    ConfigManager configManager = new ConfigManager(vertx, TEST_CONFIG_FILE);
    configManager
        .init()
        .onComplete(
            testContext.succeeding(
                v -> {
                  testContext.verify(
                      () -> {
                        assertEquals("custom-model", configManager.getModel());
                        assertEquals(0.7, configManager.getTemperature());
                        assertEquals("http://test.url", configManager.getBaseUrl());
                        assertEquals(
                            "moonshot-v1-8k",
                            configManager.getUtilityModel()); // Should have default
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testImmediateConfigAvailability(Vertx vertx) throws IOException {
    JsonObject primaryModel =
        new JsonObject()
            .put("name", "immediate-model")
            .put("temperature", 0.9)
            .put("type", "openai");

    JsonObject customConfig = new JsonObject().put("models", Map.of("primary", primaryModel));

    Files.write(Paths.get(TEST_CONFIG_FILE), customConfig.encodePrettily().getBytes());

    // Instantiate and check immediately
    ConfigManager configManager = new ConfigManager(vertx, TEST_CONFIG_FILE);

    assertEquals("immediate-model", configManager.getModel());
    assertEquals(0.9, configManager.getTemperature());
  }

  @Test
  void testHotReload(Vertx vertx, VertxTestContext testContext) throws IOException {
    ConfigManager configManager = new ConfigManager(vertx, TEST_CONFIG_FILE);
    configManager
        .init()
        .onComplete(
            testContext.succeeding(
                v -> {
                  testContext.verify(
                      () -> {
                        assertEquals("kimi-k2-0905-preview", configManager.getModel());

                        // Register listener for change
                        configManager.listen(
                            newConfig -> {
                              ModelConfig mc = newConfig.getModel("primary");
                              if (mc != null && "reloaded-model".equals(mc.name())) {
                                testContext.completeNow();
                              }
                            });

                        // Update file with structured data
                        JsonObject reloadedModel =
                            new JsonObject().put("name", "reloaded-model").put("type", "openai");

                        JsonObject updatedConfig =
                            new JsonObject().put("models", Map.of("primary", reloadedModel));

                        try {
                          Files.write(
                              Paths.get(TEST_CONFIG_FILE),
                              updatedConfig.encodePrettily().getBytes());
                        } catch (IOException e) {
                          testContext.failNow(e);
                        }
                      });
                }));
  }

  @Test
  void testAutoInitialization(Vertx vertx, VertxTestContext testContext) {
    // Ensure clean state
    try {
      Files.deleteIfExists(Paths.get(TEST_CONFIG_FILE));
    } catch (IOException e) {
      testContext.failNow(e);
      return;
    }

    ConfigManager configManager = new ConfigManager(vertx, TEST_CONFIG_FILE);
    configManager
        .init()
        .onComplete(
            testContext.succeeding(
                v -> {
                  testContext.verify(
                      () -> {
                        assertEquals(true, Files.exists(Paths.get(TEST_CONFIG_FILE)));
                        JsonObject created =
                            new JsonObject(
                                new String(Files.readAllBytes(Paths.get(TEST_CONFIG_FILE))));
                        assertEquals(
                            "kimi-k2-0905-preview",
                            created
                                .getJsonObject("models")
                                .getJsonObject("primary")
                                .getString("name"));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testNoOverwrite(Vertx vertx, VertxTestContext testContext) throws IOException {
    // Create custom config
    JsonObject custom =
        new JsonObject()
            .put(
                "models",
                Map.of(
                    "primary",
                    new JsonObject().put("name", "preserved-model").put("type", "openai")));
    Files.write(Paths.get(TEST_CONFIG_FILE), custom.encodePrettily().getBytes());

    ConfigManager configManager = new ConfigManager(vertx, TEST_CONFIG_FILE);
    configManager
        .init()
        .onComplete(
            testContext.succeeding(
                v -> {
                  testContext.verify(
                      () -> {
                        assertEquals("preserved-model", configManager.getModel());
                        testContext.completeNow();
                      });
                }));
  }
}
