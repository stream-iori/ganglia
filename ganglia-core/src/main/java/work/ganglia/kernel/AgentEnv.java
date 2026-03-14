package work.ganglia.kernel;

import io.vertx.core.Vertx;
import work.ganglia.config.AgentConfigProvider;
import work.ganglia.config.ModelConfigProvider;
import work.ganglia.kernel.loop.FaultTolerancePolicy;
import work.ganglia.kernel.task.AgentTaskFactory;
import work.ganglia.port.external.llm.ModelGateway;
import work.ganglia.port.internal.state.ObservationDispatcher;
import work.ganglia.port.internal.memory.ContextCompressor;
import work.ganglia.port.internal.memory.MemoryService;
import work.ganglia.port.internal.prompt.PromptEngine;
import work.ganglia.port.internal.state.ContextOptimizer;
import work.ganglia.port.internal.state.SessionManager;

/**
 * Aggregates all core services and dependencies required by the Agent Kernel.
 * This simplifies constructor signatures and improves maintainability.
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

    public AgentEnv(Vertx vertx, ModelGateway modelGateway, SessionManager sessionManager, PromptEngine promptEngine, 
                    AgentConfigProvider configProvider, ModelConfigProvider modelConfig, ContextCompressor compressor, 
                    MemoryService memoryService, ObservationDispatcher dispatcher, FaultTolerancePolicy faultTolerancePolicy, 
                    ContextOptimizer contextOptimizer) {
        this.vertx = vertx;
        this.modelGateway = modelGateway;
        this.sessionManager = sessionManager;
        this.promptEngine = promptEngine;
        this.configProvider = configProvider;
        this.modelConfig = modelConfig;
        this.compressor = compressor;
        this.memoryService = memoryService;
        this.dispatcher = dispatcher;
        this.faultTolerancePolicy = faultTolerancePolicy;
        this.contextOptimizer = contextOptimizer;
    }

    public Vertx vertx() { return vertx; }
    public ModelGateway modelGateway() { return modelGateway; }
    public SessionManager sessionManager() { return sessionManager; }
    public PromptEngine promptEngine() { return promptEngine; }
    public AgentConfigProvider configProvider() { return configProvider; }
    public ModelConfigProvider modelConfig() { return modelConfig; }
    public ContextCompressor compressor() { return compressor; }
    public MemoryService memoryService() { return memoryService; }
    public ObservationDispatcher dispatcher() { return dispatcher; }
    public FaultTolerancePolicy faultTolerancePolicy() { return faultTolerancePolicy; }
    public ContextOptimizer contextOptimizer() { return contextOptimizer; }
    public AgentTaskFactory taskFactory() { return taskFactory; }
    public void setTaskFactory(AgentTaskFactory taskFactory) { this.taskFactory = taskFactory; }
}
