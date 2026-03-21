package work.ganglia.kernel;

import io.vertx.core.Vertx;
import work.ganglia.config.AgentConfigProvider;
import work.ganglia.config.ModelConfigProvider;
import work.ganglia.kernel.loop.FaultTolerancePolicy;
import work.ganglia.kernel.task.AgentTaskFactory;
import work.ganglia.port.external.llm.ModelGateway;
import work.ganglia.port.internal.memory.ContextCompressor;
import work.ganglia.port.internal.memory.MemoryService;
import work.ganglia.port.internal.prompt.PromptEngine;
import work.ganglia.port.internal.state.ContextOptimizer;
import work.ganglia.port.internal.state.ObservationDispatcher;
import work.ganglia.port.internal.state.SessionManager;

/**
 * Aggregates all core services and dependencies required by the Agent Kernel. This acts as a
 * centralized container for application-wide singletons.
 */
public class AgentEnv {
  private final Vertx vertx;
  private final ModelGateway modelGateway;
  private final SessionManager sessionManager;
  private final PromptEngine promptEngine;
  private final AgentConfigProvider configProvider;
  private final ModelConfigProvider modelConfig;
  private final ContextCompressor compressor;
  private final MemoryService memoryService;
  private final ObservationDispatcher dispatcher;
  private final FaultTolerancePolicy faultTolerancePolicy;
  private final ContextOptimizer contextOptimizer;
  private AgentTaskFactory taskFactory;

  private AgentEnv(Builder builder) {
    this.vertx = builder.vertx;
    this.modelGateway = builder.modelGateway;
    this.sessionManager = builder.sessionManager;
    this.promptEngine = builder.promptEngine;
    this.configProvider = builder.configProvider;
    this.modelConfig = builder.modelConfig;
    this.compressor = builder.compressor;
    this.memoryService = builder.memoryService;
    this.dispatcher = builder.dispatcher;
    this.faultTolerancePolicy = builder.faultTolerancePolicy;
    this.contextOptimizer = builder.contextOptimizer;
    this.taskFactory = builder.taskFactory;
  }

  public static Builder builder() {
    return new Builder();
  }

  public Vertx vertx() {
    return vertx;
  }

  public ModelGateway modelGateway() {
    return modelGateway;
  }

  public SessionManager sessionManager() {
    return sessionManager;
  }

  public PromptEngine promptEngine() {
    return promptEngine;
  }

  public AgentConfigProvider configProvider() {
    return configProvider;
  }

  public ModelConfigProvider modelConfig() {
    return modelConfig;
  }

  public ContextCompressor compressor() {
    return compressor;
  }

  public MemoryService memoryService() {
    return memoryService;
  }

  public ObservationDispatcher dispatcher() {
    return dispatcher;
  }

  public FaultTolerancePolicy faultTolerancePolicy() {
    return faultTolerancePolicy;
  }

  public ContextOptimizer contextOptimizer() {
    return contextOptimizer;
  }

  public AgentTaskFactory taskFactory() {
    return taskFactory;
  }

  public void setTaskFactory(AgentTaskFactory taskFactory) {
    this.taskFactory = taskFactory;
  }

  public static class Builder {
    private Vertx vertx;
    private ModelGateway modelGateway;
    private SessionManager sessionManager;
    private PromptEngine promptEngine;
    private AgentConfigProvider configProvider;
    private ModelConfigProvider modelConfig;
    private ContextCompressor compressor;
    private MemoryService memoryService;
    private ObservationDispatcher dispatcher;
    private FaultTolerancePolicy faultTolerancePolicy;
    private ContextOptimizer contextOptimizer;
    private AgentTaskFactory taskFactory;

    private Builder() {}

    public Builder vertx(Vertx vertx) {
      this.vertx = vertx;
      return this;
    }

    public Builder modelGateway(ModelGateway modelGateway) {
      this.modelGateway = modelGateway;
      return this;
    }

    public Builder sessionManager(SessionManager sessionManager) {
      this.sessionManager = sessionManager;
      return this;
    }

    public Builder promptEngine(PromptEngine promptEngine) {
      this.promptEngine = promptEngine;
      return this;
    }

    public Builder configProvider(AgentConfigProvider configProvider) {
      this.configProvider = configProvider;
      return this;
    }

    public Builder modelConfig(ModelConfigProvider modelConfig) {
      this.modelConfig = modelConfig;
      return this;
    }

    public Builder compressor(ContextCompressor compressor) {
      this.compressor = compressor;
      return this;
    }

    public Builder memoryService(MemoryService memoryService) {
      this.memoryService = memoryService;
      return this;
    }

    public Builder dispatcher(ObservationDispatcher dispatcher) {
      this.dispatcher = dispatcher;
      return this;
    }

    public Builder faultTolerancePolicy(FaultTolerancePolicy faultTolerancePolicy) {
      this.faultTolerancePolicy = faultTolerancePolicy;
      return this;
    }

    public Builder contextOptimizer(ContextOptimizer contextOptimizer) {
      this.contextOptimizer = contextOptimizer;
      return this;
    }

    public Builder taskFactory(AgentTaskFactory taskFactory) {
      this.taskFactory = taskFactory;
      return this;
    }

    public AgentEnv build() {
      return new AgentEnv(this);
    }
  }
}
