package work.ganglia;

import io.vertx.core.json.JsonObject;
import java.util.Collections;
import java.util.List;
import work.ganglia.kernel.loop.AgentLoopObserver;
import work.ganglia.port.external.llm.ModelGateway;
import work.ganglia.port.external.tool.CommandExecutor;
import work.ganglia.port.external.tool.ToolSet;
import work.ganglia.port.external.tool.ToolSetProvider;
import work.ganglia.port.internal.prompt.ContextSource;

/** Configuration options for bootstrapping Ganglia. */
public record BootstrapOptions(
    String configPath,
    JsonObject overrideConfig,
    ModelGateway modelGatewayOverride,
    List<AgentLoopObserver> extraObservers,
    String projectRoot,
    List<ToolSet> extraToolSets,
    List<ToolSetProvider> extraToolSetProviders,
    List<ContextSource> extraContextSources,
    CommandExecutor commandExecutor) {
  public static BootstrapOptions defaultOptions() {
    return new BootstrapOptions(
        null,
        null,
        null,
        Collections.emptyList(),
        null,
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList(),
        null);
  }

  public BootstrapOptions withConfigPath(String path) {
    return new BootstrapOptions(
        path,
        overrideConfig,
        modelGatewayOverride,
        extraObservers,
        projectRoot,
        extraToolSets,
        extraToolSetProviders,
        extraContextSources,
        commandExecutor);
  }

  public BootstrapOptions withOverrideConfig(JsonObject config) {
    return new BootstrapOptions(
        configPath,
        config,
        modelGatewayOverride,
        extraObservers,
        projectRoot,
        extraToolSets,
        extraToolSetProviders,
        extraContextSources,
        commandExecutor);
  }

  public BootstrapOptions withModelGateway(ModelGateway gateway) {
    return new BootstrapOptions(
        configPath,
        overrideConfig,
        gateway,
        extraObservers,
        projectRoot,
        extraToolSets,
        extraToolSetProviders,
        extraContextSources,
        commandExecutor);
  }

  public BootstrapOptions withObservers(List<AgentLoopObserver> observers) {
    return new BootstrapOptions(
        configPath,
        overrideConfig,
        modelGatewayOverride,
        observers,
        projectRoot,
        extraToolSets,
        extraToolSetProviders,
        extraContextSources,
        commandExecutor);
  }

  public BootstrapOptions withProjectRoot(String root) {
    return new BootstrapOptions(
        configPath,
        overrideConfig,
        modelGatewayOverride,
        extraObservers,
        root,
        extraToolSets,
        extraToolSetProviders,
        extraContextSources,
        commandExecutor);
  }

  public BootstrapOptions withExtraToolSets(List<ToolSet> toolSets) {
    return new BootstrapOptions(
        configPath,
        overrideConfig,
        modelGatewayOverride,
        extraObservers,
        projectRoot,
        toolSets,
        extraToolSetProviders,
        extraContextSources,
        commandExecutor);
  }

  public BootstrapOptions withExtraToolSetProviders(List<ToolSetProvider> toolSetProviders) {
    return new BootstrapOptions(
        configPath,
        overrideConfig,
        modelGatewayOverride,
        extraObservers,
        projectRoot,
        extraToolSets,
        toolSetProviders,
        extraContextSources,
        commandExecutor);
  }

  public BootstrapOptions withExtraContextSources(List<ContextSource> contextSources) {
    return new BootstrapOptions(
        configPath,
        overrideConfig,
        modelGatewayOverride,
        extraObservers,
        projectRoot,
        extraToolSets,
        extraToolSetProviders,
        contextSources,
        commandExecutor);
  }

  public BootstrapOptions withCommandExecutor(CommandExecutor executor) {
    return new BootstrapOptions(
        configPath,
        overrideConfig,
        modelGatewayOverride,
        extraObservers,
        projectRoot,
        extraToolSets,
        extraToolSetProviders,
        extraContextSources,
        executor);
  }
}
