package work.ganglia.kernel;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

import work.ganglia.BootstrapOptions;
import work.ganglia.Ganglia;
import work.ganglia.config.ConfigManager;
import work.ganglia.infrastructure.external.llm.ModelGatewayFactory;
import work.ganglia.infrastructure.external.llm.RetryingModelGateway;
import work.ganglia.infrastructure.external.tool.DefaultToolExecutor;
import work.ganglia.infrastructure.external.tool.InteractionTools;
import work.ganglia.infrastructure.external.tool.KnowledgeBaseTools;
import work.ganglia.infrastructure.external.tool.ToolsFactory;
import work.ganglia.infrastructure.internal.prompt.StandardPromptEngine;
import work.ganglia.infrastructure.internal.prompt.context.DailyContextSource;
import work.ganglia.infrastructure.internal.skill.DefaultSkillRuntime;
import work.ganglia.infrastructure.internal.skill.DefaultSkillService;
import work.ganglia.infrastructure.internal.skill.FileSystemSkillLoader;
import work.ganglia.infrastructure.internal.skill.JarSkillLoader;
import work.ganglia.infrastructure.internal.state.DefaultContextOptimizer;
import work.ganglia.infrastructure.internal.state.DefaultSessionManager;
import work.ganglia.infrastructure.internal.state.FileLogManager;
import work.ganglia.infrastructure.internal.state.FileStateEngine;
import work.ganglia.infrastructure.internal.state.TokenUsageManager;
import work.ganglia.infrastructure.internal.state.TraceManager;
import work.ganglia.infrastructure.mcp.McpToolSet;
import work.ganglia.kernel.doctor.DefaultDoctorService;
import work.ganglia.kernel.hook.InterceptorPipeline;
import work.ganglia.kernel.hook.builtin.ObservationCompressionHook;
import work.ganglia.kernel.hook.builtin.TokenAwareTruncator;
import work.ganglia.kernel.loop.AgentLoopFactory;
import work.ganglia.kernel.loop.ConsecutiveFailurePolicy;
import work.ganglia.kernel.loop.DefaultObservationDispatcher;
import work.ganglia.kernel.loop.ReActAgentLoop;
import work.ganglia.kernel.subagent.DefaultGraphExecutor;
import work.ganglia.kernel.subagent.GraphExecutor;
import work.ganglia.kernel.task.AgentTaskFactory;
import work.ganglia.kernel.task.DefaultAgentTaskFactory;
import work.ganglia.kernel.todo.ToDoTools;
import work.ganglia.port.external.llm.ModelGateway;
import work.ganglia.port.external.tool.ToolSet;
import work.ganglia.port.internal.memory.MemorySystem;
import work.ganglia.port.internal.memory.MemorySystemConfig;
import work.ganglia.port.internal.memory.MemorySystemProvider;
import work.ganglia.port.internal.skill.SkillLoader;
import work.ganglia.port.internal.skill.SkillRuntime;
import work.ganglia.port.internal.skill.SkillService;
import work.ganglia.port.internal.state.SessionManager;
import work.ganglia.util.Constants;
import work.ganglia.util.FileSystemUtil;
import work.ganglia.util.TokenCounter;

/**
 * SRP: Orchestrates the initialization and wiring of all Ganglia core components. DIP: Manages the
 * dependency graph and provides a clean entry point.
 */
public class GangliaKernel {
  private static final Logger logger = LoggerFactory.getLogger(GangliaKernel.class);

  private final Vertx vertx;
  private final BootstrapOptions options;
  private final ConfigManager configManager;

  public GangliaKernel(Vertx vertx, BootstrapOptions options) {
    this.vertx = vertx;
    this.options = options;
    this.configManager =
        options.configPath() != null
            ? new ConfigManager(vertx, options.configPath())
            : new ConfigManager(vertx);

    if (options.overrideConfig() != null) {
      configManager.updateConfig(options.overrideConfig());
    }
  }

  public Future<Ganglia> init() {
    final String projectRoot =
        options.projectRoot() != null ? options.projectRoot() : configManager.getProjectRoot();

    return configManager
        .init()
        .compose(v -> new DefaultDoctorService(vertx, options.doctorChecks()).runStartupChecks())
        .compose(
            results -> {
              logger.info("Configuration initialized. Starting self-check...");
              return ensureCoreStructure(projectRoot);
            })
        .compose(v -> initializeSkillSystem(projectRoot))
        .compose(
            skillService ->
                work.ganglia.infrastructure.mcp.McpConfigManager.loadMcpToolSets(vertx, projectRoot)
                    .map(mcpRegistry -> new BootstrapContext(skillService, mcpRegistry)))
        .compose(
            bootstrap ->
                assembleSystem(projectRoot, bootstrap.skillService, bootstrap.mcpRegistry));
  }

  private Future<Void> ensureCoreStructure(String projectRoot) {
    List<Future<Void>> dirFutures = new ArrayList<>();
    dirFutures.add(
        FileSystemUtil.ensureDirectoryExists(
            vertx, Paths.get(projectRoot, Constants.DIR_SKILLS).toString()));
    dirFutures.add(
        FileSystemUtil.ensureDirectoryExists(
            vertx, Paths.get(projectRoot, Constants.DIR_MEMORY).toString()));
    dirFutures.add(
        FileSystemUtil.ensureDirectoryExists(
            vertx, Paths.get(projectRoot, Constants.DIR_STATE).toString()));
    dirFutures.add(
        FileSystemUtil.ensureDirectoryExists(
            vertx, Paths.get(projectRoot, Constants.DIR_LOGS).toString()));
    dirFutures.add(
        FileSystemUtil.ensureDirectoryExists(
            vertx, Paths.get(projectRoot, Constants.DIR_TRACE).toString()));
    dirFutures.add(
        FileSystemUtil.ensureDirectoryExists(
            vertx, Paths.get(projectRoot, Constants.DIR_TMP).toString()));
    return Future.join(dirFutures).map(v -> null);
  }

  private Future<SkillService> initializeSkillSystem(String projectRoot) {
    List<Path> skillPaths = new ArrayList<>();
    skillPaths.add(Paths.get(projectRoot, Constants.DIR_SKILLS));
    String userHome = System.getProperty("user.home");
    if (userHome != null) {
      skillPaths.add(Paths.get(userHome, ".ganglia/skills"));
    }
    skillPaths.add(Paths.get(projectRoot, "skills"));

    List<SkillLoader> loaders = new ArrayList<>();
    loaders.add(new FileSystemSkillLoader(vertx, skillPaths));
    loaders.add(new JarSkillLoader(vertx, skillPaths));

    SkillService skillService = new DefaultSkillService(loaders);
    return skillService.init().map(v -> skillService);
  }

  private Future<Ganglia> assembleSystem(
      String projectRoot,
      SkillService skillService,
      work.ganglia.infrastructure.mcp.McpRegistry mcpRegistry) {
    ModelGateway rawGateway =
        options.modelGatewayOverride() != null
            ? options.modelGatewayOverride()
            : ModelGatewayFactory.create(vertx, configManager);
    ModelGateway modelGateway = new RetryingModelGateway(rawGateway, vertx);

    // Discover memory system via ServiceLoader (SPI)
    MemorySystemProvider memoryProvider =
        ServiceLoader.load(MemorySystemProvider.class)
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "No MemorySystemProvider on classpath. Add ganglia-memory dependency."));

    String compressionModel =
        configManager.getUtilityModel() != null
            ? configManager.getUtilityModel()
            : configManager.getModel();

    MemorySystem memory =
        memoryProvider.create(
            new MemorySystemConfig(
                vertx, projectRoot, modelGateway, configManager, compressionModel));

    // Initialize long-term memory and then wire the rest of the system
    return memory
        .longTermMemory()
        .ensureInitialized()
        .compose(v -> buildGanglia(projectRoot, skillService, mcpRegistry, modelGateway, memory));
  }

  private Future<Ganglia> buildGanglia(
      String projectRoot,
      SkillService skillService,
      work.ganglia.infrastructure.mcp.McpRegistry mcpRegistry,
      ModelGateway modelGateway,
      MemorySystem memory) {
    SkillRuntime skillRuntime = new DefaultSkillRuntime(vertx, skillService);
    TokenCounter tokenCounter = new TokenCounter();

    DefaultObservationDispatcher dispatcher = new DefaultObservationDispatcher(vertx);

    // Wire dispatcher into RetryingModelGateway for MODEL_CALL observations
    if (modelGateway instanceof RetryingModelGateway retrying) {
      retrying.setDispatcher(dispatcher);
    }

    // Wire dispatcher into McpToolSets for MCP_CALL observations
    if (mcpRegistry != null && mcpRegistry.toolSets() != null) {
      for (ToolSet ts : mcpRegistry.toolSets()) {
        if (ts instanceof McpToolSet mcpTs) {
          mcpTs.setDispatcher(dispatcher);
        }
      }
    }

    // Register extra observers directly
    for (work.ganglia.kernel.loop.AgentLoopObserver observer : options.extraObservers()) {
      dispatcher.register(observer);
    }

    InterceptorPipeline pipeline = new InterceptorPipeline();
    pipeline.addInterceptor(
        new ObservationCompressionHook(
            memory.observationCompressor(),
            memory.memoryStore(),
            // 1 500 tokens keeps a degraded turn well within the 2 000-token history budget,
            // so one LLM-compression failure does not crowd out all other turns.
            new TokenAwareTruncator(tokenCounter, 1500),
            vertx,
            projectRoot));

    ToolsFactory toolsFactory = new ToolsFactory(vertx, projectRoot);
    StandardPromptEngine promptEngine =
        new StandardPromptEngine(
            vertx,
            memory.memoryService(),
            skillRuntime,
            null,
            tokenCounter,
            options.extraContextSources(),
            configManager);
    promptEngine.addContextSource(
        new DailyContextSource(vertx, Paths.get(projectRoot, Constants.DIR_MEMORY).toString()));
    promptEngine.addContextSource(memory.memoryContextSource());

    SessionManager sessionManager =
        new DefaultSessionManager(
            new FileStateEngine(vertx), new FileLogManager(vertx), configManager);

    List<ToolSet> allExtraToolSets = new ArrayList<>(options.extraToolSets());
    if (mcpRegistry != null && mcpRegistry.toolSets() != null) {
      allExtraToolSets.addAll(mcpRegistry.toolSets());
    }
    allExtraToolSets.add(new ToDoTools(vertx, memory.contextCompressor()));
    allExtraToolSets.add(new KnowledgeBaseTools(vertx, memory.longTermMemory()));
    allExtraToolSets.add(new InteractionTools(vertx));
    allExtraToolSets.add(
        new work.ganglia.infrastructure.external.tool.RecallMemoryTools(memory.memoryStore()));

    DefaultToolExecutor toolExecutor = new DefaultToolExecutor(toolsFactory, allExtraToolSets);

    ConsecutiveFailurePolicy failurePolicy = new ConsecutiveFailurePolicy();
    DefaultContextOptimizer contextOptimizer =
        new DefaultContextOptimizer(
            configManager, configManager, memory.contextCompressor(), tokenCounter);

    // 1. Build AgentEnv first (with taskFactory = null initially)
    AgentEnv env =
        AgentEnv.builder()
            .vertx(vertx)
            .modelGateway(modelGateway)
            .sessionManager(sessionManager)
            .promptEngine(promptEngine)
            .configProvider(configManager)
            .modelConfig(configManager)
            .compressor(memory.contextCompressor())
            .memoryService(memory.memoryService())
            .dispatcher(dispatcher)
            .faultTolerancePolicy(failurePolicy)
            .contextOptimizer(contextOptimizer)
            .build();

    // 2. Define AgentLoopFactory with late-binding taskFactory from env
    AgentLoopFactory loopFactory =
        () ->
            ReActAgentLoop.builder()
                .vertx(vertx)
                .dispatcher(dispatcher)
                .sessionManager(sessionManager)
                .configProvider(configManager)
                .contextOptimizer(contextOptimizer)
                .promptEngine(promptEngine)
                .modelGateway(modelGateway)
                .taskFactory(env.taskFactory()) // Late binding
                .faultTolerancePolicy(failurePolicy)
                .contextCompressor(memory.contextCompressor())
                .pipeline(pipeline)
                .build();

    // 3. Complete the dependency graph
    GraphExecutor graphExecutor = new DefaultGraphExecutor(loopFactory);
    AgentTaskFactory taskFactory =
        new DefaultAgentTaskFactory(
            loopFactory, toolExecutor, graphExecutor, skillService, skillRuntime);

    // 4. Wire back the cross-references
    env.setTaskFactory(taskFactory);
    promptEngine.setTaskFactory(taskFactory);
    graphExecutor.initialize(taskFactory);

    TraceManager traceManager = new TraceManager(vertx, configManager);
    TokenUsageManager tokenUsageManager = new TokenUsageManager(vertx, tokenCounter);

    ReActAgentLoop primaryAgentLoop = (ReActAgentLoop) loopFactory.createLoop();

    int mcpCount =
        mcpRegistry != null && mcpRegistry.toolSets() != null ? mcpRegistry.toolSets().size() : 0;
    return Future.succeededFuture(
        new Ganglia(
            vertx,
            modelGateway,
            toolExecutor,
            sessionManager,
            primaryAgentLoop,
            configManager,
            env,
            mcpCount,
            mcpRegistry,
            traceManager,
            tokenUsageManager));
  }

  private record BootstrapContext(
      SkillService skillService, work.ganglia.infrastructure.mcp.McpRegistry mcpRegistry) {}
}
