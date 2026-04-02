package work.ganglia.kernel;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicReference;

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
import work.ganglia.infrastructure.internal.state.DefaultContextEventPublisher;
import work.ganglia.infrastructure.internal.state.DefaultContextOptimizer;
import work.ganglia.infrastructure.internal.state.DefaultSessionManager;
import work.ganglia.infrastructure.internal.state.FileLogManager;
import work.ganglia.infrastructure.internal.state.FileStateEngine;
import work.ganglia.infrastructure.internal.state.ContextPressureMonitor;
import work.ganglia.infrastructure.internal.state.TokenUsageManager;
import work.ganglia.infrastructure.internal.state.TraceManager;
import work.ganglia.port.internal.state.ContextEventPublisher;
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
import work.ganglia.port.internal.prompt.ContextManagementConfig;
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

    ContextManagementConfig config =
        ContextManagementConfig.fromModel(
            configManager.getContextLimit(), configManager.getMaxTokens());

    InterceptorPipeline pipeline = new InterceptorPipeline();
    pipeline.addInterceptor(
        new ObservationCompressionHook(
            memory.observationCompressor(),
            memory.memoryStore(),
            new TokenAwareTruncator(tokenCounter, config.budget().observationFallback()),
            vertx,
            projectRoot));

    ToolsFactory toolsFactory = new ToolsFactory(vertx, projectRoot);
    AtomicReference<AgentTaskFactory> taskFactoryRef = new AtomicReference<>();

    StandardPromptEngine promptEngine =
        new StandardPromptEngine(
            vertx,
            memory.memoryService(),
            skillRuntime,
            taskFactoryRef::get,
            tokenCounter,
            options.extraContextSources(),
            configManager,
            config);
    promptEngine.setDispatcher(dispatcher);
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

    // Register common tools (filesystem, bash, grep, web fetch)
    work.ganglia.util.PathMapper defaultMapper = new work.ganglia.util.PathSanitizer(projectRoot);
    work.ganglia.port.external.tool.CommandExecutor cmdExecutor = options.commandExecutor();
    if (cmdExecutor == null) {
      cmdExecutor = new work.ganglia.infrastructure.external.tool.util.LocalCommandExecutor(vertx);
    }
    allExtraToolSets.add(
        new work.ganglia.infrastructure.external.tool.NativeFileSystemTools(vertx, defaultMapper));
    allExtraToolSets.add(
        new work.ganglia.infrastructure.external.tool.BashFileSystemTools(
            cmdExecutor, defaultMapper));
    allExtraToolSets.add(new work.ganglia.infrastructure.external.tool.BashTools(cmdExecutor));
    allExtraToolSets.add(new work.ganglia.infrastructure.external.tool.WebFetchTools(vertx));

    DefaultToolExecutor toolExecutor = new DefaultToolExecutor(toolsFactory, allExtraToolSets);

    ConsecutiveFailurePolicy failurePolicy = new ConsecutiveFailurePolicy();
    DefaultContextOptimizer contextOptimizer =
        new DefaultContextOptimizer(
            configManager,
            configManager,
            memory.contextCompressor(),
            tokenCounter,
            dispatcher,
            config);

    // Create ContextEventPublisher and ContextPressureMonitor
    ContextEventPublisher eventPublisher = new DefaultContextEventPublisher(dispatcher);
    ContextPressureMonitor pressureMonitor =
        new ContextPressureMonitor(config.budget(), tokenCounter, eventPublisher);

    // 1. Build AgentEnv with supplier-based taskFactory (resolved lazily)
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
            .taskFactoryProvider(taskFactoryRef::get)
            .build();

    // 2. Define AgentLoopFactory with supplier-based taskFactory (resolved at loop creation time)
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
                .taskFactory(taskFactoryRef.get())
                .faultTolerancePolicy(failurePolicy)
                .contextCompressor(memory.contextCompressor())
                .pressureMonitor(pressureMonitor)
                .pipeline(pipeline)
                .build();

    // 3. Complete the dependency graph — no late-binding setters needed
    GraphExecutor graphExecutor = new DefaultGraphExecutor(loopFactory);
    AgentTaskFactory taskFactory =
        new DefaultAgentTaskFactory(
            loopFactory, toolExecutor, graphExecutor, skillService, skillRuntime);
    taskFactoryRef.set(taskFactory);

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
