package work.ganglia;

import io.vertx.core.json.JsonObject;
import work.ganglia.kernel.loop.AgentLoopObserver;
import work.ganglia.port.external.llm.ModelGateway;

import java.util.Collections;
import java.util.List;

/**
 * Configuration options for bootstrapping Ganglia.
 */
public record BootstrapOptions(
    String configPath,
    JsonObject overrideConfig,
    ModelGateway modelGatewayOverride,
    List<AgentLoopObserver> extraObservers
) {
    public static BootstrapOptions defaultOptions() {
        return new BootstrapOptions(null, null, null, Collections.emptyList());
    }

    public BootstrapOptions withConfigPath(String path) {
        return new BootstrapOptions(path, overrideConfig, modelGatewayOverride, extraObservers);
    }

    public BootstrapOptions withOverrideConfig(JsonObject config) {
        return new BootstrapOptions(configPath, config, modelGatewayOverride, extraObservers);
    }

    public BootstrapOptions withModelGateway(ModelGateway gateway) {
        return new BootstrapOptions(configPath, overrideConfig, gateway, extraObservers);
    }

    public BootstrapOptions withObservers(List<AgentLoopObserver> observers) {
        return new BootstrapOptions(configPath, overrideConfig, modelGatewayOverride, observers);
    }
}
