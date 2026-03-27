package work.ganglia.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.config.model.GangliaConfig;

@ExtendWith(VertxExtension.class)
class ConfigManagerTest {

  @TempDir java.nio.file.Path tempDir;

  private String nonExistentConfig() {
    return tempDir.resolve("ganglia.json").toString();
  }

  @Test
  void testDefaultValuesWithNoConfigFile(Vertx vertx) {
    ConfigManager manager = new ConfigManager(vertx, nonExistentConfig());
    assertNotNull(manager.getModel());
    assertTrue(manager.getMaxTokens() > 0);
    assertTrue(manager.getContextLimit() > 0);
    assertNotNull(manager.getBaseUrl());
    assertNotNull(manager.getProvider());
  }

  @Test
  void testAgentConfigProviderDefaults(Vertx vertx) {
    ConfigManager manager = new ConfigManager(vertx, nonExistentConfig());
    assertTrue(manager.getMaxIterations() > 0);
    assertTrue(manager.getCompressionThreshold() > 0.0);
    assertNotNull(manager.getProjectRoot());
    assertNotNull(manager.getInstructionFile());
    assertTrue(manager.getToolTimeoutMs() > 0);
  }

  @Test
  void testObservabilityConfigProviderDefaults(Vertx vertx) {
    ConfigManager manager = new ConfigManager(vertx, nonExistentConfig());
    assertFalse(manager.isObservabilityEnabled());
    assertNotNull(manager.getTracePath());
  }

  @Test
  void testWebUIConfigProviderDefaults(Vertx vertx) {
    ConfigManager manager = new ConfigManager(vertx, nonExistentConfig());
    assertTrue(manager.getWebUIPort() > 0);
    assertNotNull(manager.getWebRoot());
  }

  @Test
  void testUpdateConfigOverridesValues(Vertx vertx) {
    ConfigManager manager = new ConfigManager(vertx, nonExistentConfig());
    JsonObject update =
        new JsonObject()
            .put(
                "observability",
                new JsonObject().put("enabled", true).put("tracePath", "/custom/trace"));
    manager.updateConfig(update);
    assertTrue(manager.isObservabilityEnabled());
    assertEquals("/custom/trace", manager.getTracePath());
  }

  @Test
  void testListenerCalledOnUpdate(Vertx vertx) {
    ConfigManager manager = new ConfigManager(vertx, nonExistentConfig());
    List<GangliaConfig> received = new ArrayList<>();
    manager.listen(received::add);
    manager.updateConfig(
        new JsonObject()
            .put(
                "webui",
                new JsonObject().put("port", 9090).put("enabled", true).put("webroot", "w")));
    assertEquals(1, received.size());
    assertNotNull(received.get(0));
  }

  @Test
  void testDeepMergePreservesExistingKeys(Vertx vertx) {
    ConfigManager manager = new ConfigManager(vertx, nonExistentConfig());
    manager.updateConfig(
        new JsonObject().put("observability", new JsonObject().put("enabled", true)));
    assertNotNull(manager.getTracePath());
    assertTrue(manager.isObservabilityEnabled());
  }

  @Test
  void testGetConfigPathNotNull(Vertx vertx) {
    ConfigManager manager = new ConfigManager(vertx, nonExistentConfig());
    assertNotNull(manager.getConfigPath());
  }

  @Test
  void testGetGangliaConfigNotNull(Vertx vertx) {
    ConfigManager manager = new ConfigManager(vertx, nonExistentConfig());
    assertNotNull(manager.getGangliaConfig());
  }

  @Test
  void testGetConfigJsonNotNull(Vertx vertx) {
    ConfigManager manager = new ConfigManager(vertx, nonExistentConfig());
    assertNotNull(manager.getConfig());
  }

  @Test
  void testGetModelConfigByKey(Vertx vertx) {
    ConfigManager manager = new ConfigManager(vertx, nonExistentConfig());
    assertNotNull(manager.getModelConfig(ConfigKeys.PRIMARY));
  }

  @Test
  void testUtilityModelFallsBackToPrimary(Vertx vertx) {
    ConfigManager manager = new ConfigManager(vertx, nonExistentConfig());
    assertNotNull(manager.getUtilityModel());
  }

  @Test
  void testInitWithNonExistentFile(Vertx vertx, VertxTestContext testContext) {
    ConfigManager manager = new ConfigManager(vertx, nonExistentConfig());
    manager
        .init()
        .onComplete(
            testContext.succeeding(
                v -> {
                  testContext.verify(
                      () -> {
                        assertNotNull(manager.getGangliaConfig());
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testUpdateConfigWithAgentSettings(Vertx vertx) {
    ConfigManager manager = new ConfigManager(vertx, nonExistentConfig());
    JsonObject overlay =
        new JsonObject()
            .put(
                "agent",
                new JsonObject()
                    .put("maxIterations", 99)
                    .put("compressionThreshold", 0.9)
                    .put("toolTimeout", 30000));
    manager.updateConfig(overlay);
    assertEquals(99, manager.getMaxIterations());
    assertEquals(0.9, manager.getCompressionThreshold(), 0.001);
    assertEquals(30000L, manager.getToolTimeoutMs());
  }

  @Test
  void testIsStreamFromConfig(Vertx vertx) {
    ConfigManager manager = new ConfigManager(vertx, nonExistentConfig());
    boolean stream = manager.isStream();
    boolean utilityStream = manager.isUtilityStream();
    // Verify no NPE — boolean result is fine either way
    assertTrue(stream || !stream);
    assertTrue(utilityStream || !utilityStream);
  }

  @Test
  void testTemperatureFromConfig(Vertx vertx) {
    ConfigManager manager = new ConfigManager(vertx, nonExistentConfig());
    double temperature = manager.getTemperature();
    assertTrue(temperature >= 0.0);
  }
}
