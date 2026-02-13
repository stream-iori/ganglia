package me.stream.ganglia.core.config;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        configManager.init().onComplete(testContext.succeeding(v -> {
            testContext.verify(() -> {
                assertEquals("gpt-4o", configManager.getModel());
                assertEquals(0.0, configManager.getTemperature());
                testContext.completeNow();
            });
        }));
    }

    @Test
    void testLoadFromFile(Vertx vertx, VertxTestContext testContext) throws IOException {
        JsonObject customConfig = new JsonObject()
                .put("model", "custom-model")
                .put("temperature", 0.7);
        Files.write(Paths.get(TEST_CONFIG_FILE), customConfig.encodePrettily().getBytes());

        ConfigManager configManager = new ConfigManager(vertx, TEST_CONFIG_FILE);
        configManager.init().onComplete(testContext.succeeding(v -> {
            testContext.verify(() -> {
                assertEquals("custom-model", configManager.getModel());
                assertEquals(0.7, configManager.getTemperature());
                assertEquals("gpt-4o-mini", configManager.getUtilityModel()); // Should have default
                testContext.completeNow();
            });
        }));
    }

    @Test
    void testHotReload(Vertx vertx, VertxTestContext testContext) throws IOException {
        ConfigManager configManager = new ConfigManager(vertx, TEST_CONFIG_FILE);
        configManager.init().onComplete(testContext.succeeding(v -> {
            testContext.verify(() -> {
                assertEquals("gpt-4o", configManager.getModel());

                // Register listener for change
                configManager.listen(newConfig -> {
                    if ("reloaded-model".equals(newConfig.getString("model"))) {
                        testContext.completeNow();
                    }
                });

                // Update file
                JsonObject updatedConfig = new JsonObject().put("model", "reloaded-model");
                try {
                    Files.write(Paths.get(TEST_CONFIG_FILE), updatedConfig.encodePrettily().getBytes());
                } catch (IOException e) {
                    testContext.failNow(e);
                }
            });
        }));
    }
}
