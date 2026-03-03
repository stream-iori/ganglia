package me.stream.ganglia.swebench.config;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import me.stream.ganglia.core.config.ConfigManager;
import me.stream.ganglia.core.config.model.AgentConfig;
import me.stream.ganglia.core.config.model.GangliaConfig;
import me.stream.ganglia.core.config.model.ModelConfig;
import me.stream.ganglia.core.config.model.ObservabilityConfig;

import java.util.Map;

public class MinimalConfigManager extends ConfigManager {
    private final Vertx vertx;

    public MinimalConfigManager(Vertx vertx) {
        super(vertx, ".ganglia/config.json");
        this.vertx = vertx;
    }

    @Override
    public GangliaConfig getGangliaConfig() {
        // Load the actual config.json since it exists
        JsonObject configJson = vertx.fileSystem().readFileBlocking(".ganglia/config.json").toJsonObject();
        
        JsonObject models = configJson.getJsonObject("models");
        JsonObject primaryJson = models.getJsonObject("primary");
        JsonObject utilityJson = models.getJsonObject("utility");
        JsonObject agentJson = configJson.getJsonObject("agent");

        ModelConfig primary = new ModelConfig(
            primaryJson.getString("name"),
            primaryJson.getDouble("temperature"),
            primaryJson.getInteger("maxTokens"),
            128000,
            primaryJson.getString("type"),
            primaryJson.getString("apiKey"),
            primaryJson.getString("baseUrl")
        );
        
        ModelConfig utility = new ModelConfig(
            utilityJson.getString("name"),
            utilityJson.getDouble("temperature"),
            utilityJson.getInteger("maxTokens"),
            128000,
            utilityJson.getString("type"),
            utilityJson.getString("apiKey"),
            utilityJson.getString("baseUrl")
        );

        AgentConfig agent = new AgentConfig(
            agentJson.getInteger("maxIterations", 30),
            0.7,
            System.getProperty("user.dir")
        );


        ObservabilityConfig obs = new ObservabilityConfig(false, ".ganglia/trace");
        
        return new GangliaConfig(agent, Map.of("primary", primary, "utility", utility), obs);
    }

    @Override
    public String getModel() { return getGangliaConfig().getModel("primary").name(); }
    @Override
    public String getUtilityModel() { return getGangliaConfig().getModel("utility").name(); }
    @Override
    public double getTemperature() { return getGangliaConfig().getModel("primary").temperature(); }
    @Override
    public int getMaxTokens() { return getGangliaConfig().getModel("primary").maxTokens(); }
    @Override
    public int getContextLimit() { return 128000; }
    @Override
    public double getCompressionThreshold() { return 0.7; }
    @Override
    public int getMaxIterations() { return getGangliaConfig().agent().maxIterations(); }
    @Override
    public String getBaseUrl() { return getGangliaConfig().getModel("primary").baseUrl(); }
}
