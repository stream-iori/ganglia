package work.ganglia;

import io.vertx.core.json.JsonObject;
import work.ganglia.kernel.loop.AgentLoopObserver;
import work.ganglia.port.external.llm.ModelGateway;
import work.ganglia.port.external.tool.ToolSet;
import work.ganglia.port.internal.prompt.ContextSource;

import java.util.Collections;
import java.util.List;

/**
 * Configuration options for bootstrapping Ganglia.
 */
public record BootstrapOptions(
    String configPath,
    JsonObject overrideConfig,
    ModelGateway modelGatewayOverride,
    List<AgentLoopObserver> extraObservers,
    String projectRoot,
    List<ToolSet> extraToolSets,
    List<ContextSource> extraContextSources
) {
    public static BootstrapOptions defaultOptions() {
        return new BootstrapOptions(null, null, null, Collections.emptyList(), null, Collections.emptyList(), Collections.emptyList());
    }

    public BootstrapOptions withConfigPath(String path) {
        return new BootstrapOptions(path, overrideConfig, modelGatewayOverride, extraObservers, projectRoot, extraToolSets, extraContextSources);
    }

    public BootstrapOptions withOverrideConfig(JsonObject config) {
        return new BootstrapOptions(configPath, config, modelGatewayOverride, extraObservers, projectRoot, extraToolSets, extraContextSources);
    }

    public BootstrapOptions withModelGateway(ModelGateway gateway) {
        return new BootstrapOptions(configPath, overrideConfig, gateway, extraObservers, projectRoot, extraToolSets, extraContextSources);
    }

    public BootstrapOptions withObservers(List<AgentLoopObserver> observers) {
        return new BootstrapOptions(configPath, overrideConfig, modelGatewayOverride, observers, projectRoot, extraToolSets, extraContextSources);
    }

    public BootstrapOptions withProjectRoot(String root) {
        return new BootstrapOptions(configPath, overrideConfig, modelGatewayOverride, extraObservers, root, extraToolSets, extraContextSources);
    }

    public BootstrapOptions withExtraToolSets(List<ToolSet> toolSets) {
        return new BootstrapOptions(configPath, overrideConfig, modelGatewayOverride, extraObservers, projectRoot, toolSets, extraContextSources);
    }

    public BootstrapOptions withExtraContextSources(List<ContextSource> contextSources) {
        return new BootstrapOptions(configPath, overrideConfig, modelGatewayOverride, extraObservers, projectRoot, extraToolSets, contextSources);
    }
}
