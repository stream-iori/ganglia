package work.ganglia.stubs;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import work.ganglia.config.ConfigManager;

public class StubConfigManager extends ConfigManager {

    public StubConfigManager(Vertx vertx) {
        super(vertx, "non-existent-config.json");
    }

    public void setConfig(JsonObject config) {
        updateConfig(config);
    }
}
