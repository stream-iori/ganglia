package work.ganglia.swebench.config;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import work.ganglia.config.ConfigManager;
import work.ganglia.config.model.AgentConfig;
import work.ganglia.config.model.GangliaConfig;
import work.ganglia.config.model.ModelConfig;
import work.ganglia.config.model.ObservabilityConfig;

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
            primaryJson.getString("baseUrl"),
            primaryJson.getBoolean("stream", true),
            60000,
            5
        );

        ModelConfig utility = new ModelConfig(
            utilityJson.getString("name"),
            utilityJson.getDouble("temperature"),
            utilityJson.getInteger("maxTokens"),
            128000,
            utilityJson.getString("type"),
            utilityJson.getString("apiKey"),
            utilityJson.getString("baseUrl"),
            utilityJson.getBoolean("stream", false),
            60000,
            5
        );

        AgentConfig agent = new AgentConfig(
            agentJson.getInteger("maxIterations", 30),
            0.7,
            System.getProperty("user.dir")
        );


        ObservabilityConfig obs = new ObservabilityConfig(false, ".ganglia/trace");

        return new GangliaConfig(agent, Map.of("primary", primary, "utility", utility), obs, new GangliaConfig.WebUIConfig(8080, false, "webroot"));
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
