package work.ganglia;

import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

import work.ganglia.config.ObservabilityConfigProvider;
import work.ganglia.kernel.loop.AgentLoopObserver;
import work.ganglia.port.external.llm.ModelGateway;
import work.ganglia.port.external.tool.CommandExecutor;
import work.ganglia.port.external.tool.ToolSet;
import work.ganglia.port.internal.doctor.DoctorCheck;
import work.ganglia.port.internal.hook.AgentInterceptor;
import work.ganglia.port.internal.prompt.ContextSource;
import work.ganglia.port.internal.state.TraceWriter;

/** Immutable configuration options for bootstrapping Ganglia. */
public final class BootstrapOptions {

  private final String configPath;
  private final JsonObject overrideConfig;
  private final ModelGateway modelGatewayOverride;
  private final List<AgentLoopObserver> extraObservers;
  private final String projectRoot;
  private final List<ToolSet> extraToolSets;
  private final List<ContextSource> extraContextSources;
  private final CommandExecutor commandExecutor;
  private final List<DoctorCheck> doctorChecks;
  private final List<AgentInterceptor> extraInterceptors;
  private final BiFunction<Vertx, ObservabilityConfigProvider, TraceWriter> traceWriterFactory;

  private BootstrapOptions(Builder builder) {
    this.configPath = builder.configPath;
    this.overrideConfig = builder.overrideConfig;
    this.modelGatewayOverride = builder.modelGatewayOverride;
    this.extraObservers = builder.extraObservers;
    this.projectRoot = builder.projectRoot;
    this.extraToolSets = builder.extraToolSets;
    this.extraContextSources = builder.extraContextSources;
    this.commandExecutor = builder.commandExecutor;
    this.doctorChecks = builder.doctorChecks;
    this.extraInterceptors = builder.extraInterceptors;
    this.traceWriterFactory = builder.traceWriterFactory;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static BootstrapOptions defaultOptions() {
    return new Builder().build();
  }

  // --- Accessors (same names as the former record for compatibility) ---

  public String configPath() {
    return configPath;
  }

  public JsonObject overrideConfig() {
    return overrideConfig;
  }

  public ModelGateway modelGatewayOverride() {
    return modelGatewayOverride;
  }

  public List<AgentLoopObserver> extraObservers() {
    return extraObservers;
  }

  public String projectRoot() {
    return projectRoot;
  }

  public List<ToolSet> extraToolSets() {
    return extraToolSets;
  }

  public List<ContextSource> extraContextSources() {
    return extraContextSources;
  }

  public CommandExecutor commandExecutor() {
    return commandExecutor;
  }

  public List<DoctorCheck> doctorChecks() {
    return doctorChecks;
  }

  public List<AgentInterceptor> extraInterceptors() {
    return extraInterceptors;
  }

  public BiFunction<Vertx, ObservabilityConfigProvider, TraceWriter> traceWriterFactory() {
    return traceWriterFactory;
  }

  /** Returns a new Builder pre-populated with values from this instance. */
  public Builder toBuilder() {
    return new Builder()
        .configPath(configPath)
        .overrideConfig(overrideConfig)
        .modelGatewayOverride(modelGatewayOverride)
        .extraObservers(extraObservers)
        .projectRoot(projectRoot)
        .extraToolSets(extraToolSets)
        .extraContextSources(extraContextSources)
        .commandExecutor(commandExecutor)
        .doctorChecks(doctorChecks)
        .extraInterceptors(extraInterceptors)
        .traceWriterFactory(traceWriterFactory);
  }

  public static final class Builder {
    private String configPath;
    private JsonObject overrideConfig;
    private ModelGateway modelGatewayOverride;
    private List<AgentLoopObserver> extraObservers = Collections.emptyList();
    private String projectRoot;
    private List<ToolSet> extraToolSets = Collections.emptyList();
    private List<ContextSource> extraContextSources = Collections.emptyList();
    private CommandExecutor commandExecutor;
    private List<DoctorCheck> doctorChecks = Collections.emptyList();
    private List<AgentInterceptor> extraInterceptors = Collections.emptyList();
    private BiFunction<Vertx, ObservabilityConfigProvider, TraceWriter> traceWriterFactory =
        (v, cfg) -> () -> Future.succeededFuture();

    private Builder() {}

    public Builder configPath(String configPath) {
      this.configPath = configPath;
      return this;
    }

    public Builder overrideConfig(JsonObject overrideConfig) {
      this.overrideConfig = overrideConfig;
      return this;
    }

    public Builder modelGatewayOverride(ModelGateway modelGatewayOverride) {
      this.modelGatewayOverride = modelGatewayOverride;
      return this;
    }

    public Builder extraObservers(List<AgentLoopObserver> extraObservers) {
      this.extraObservers = extraObservers;
      return this;
    }

    public Builder projectRoot(String projectRoot) {
      this.projectRoot = projectRoot;
      return this;
    }

    public Builder extraToolSets(List<ToolSet> extraToolSets) {
      this.extraToolSets = extraToolSets;
      return this;
    }

    public Builder extraContextSources(List<ContextSource> extraContextSources) {
      this.extraContextSources = extraContextSources;
      return this;
    }

    public Builder commandExecutor(CommandExecutor commandExecutor) {
      this.commandExecutor = commandExecutor;
      return this;
    }

    public Builder doctorChecks(List<DoctorCheck> doctorChecks) {
      this.doctorChecks = doctorChecks;
      return this;
    }

    public Builder extraInterceptors(List<AgentInterceptor> extraInterceptors) {
      this.extraInterceptors = extraInterceptors;
      return this;
    }

    public Builder traceWriterFactory(
        BiFunction<Vertx, ObservabilityConfigProvider, TraceWriter> traceWriterFactory) {
      this.traceWriterFactory = traceWriterFactory;
      return this;
    }

    public BootstrapOptions build() {
      return new BootstrapOptions(this);
    }
  }
}
