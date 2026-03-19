package work.ganglia.kernel;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.ganglia.BootstrapOptions;
import work.ganglia.port.external.tool.ToolSet;
import work.ganglia.port.external.tool.ToolSetProvider;
import work.ganglia.Ganglia;
import work.ganglia.config.*;
import work.ganglia.infrastructure.external.llm.ModelGatewayFactory;
import work.ganglia.infrastructure.external.llm.RetryingModelGateway;
import work.ganglia.infrastructure.external.tool.DefaultToolExecutor;
import work.ganglia.infrastructure.external.tool.ToolsFactory;
import work.ganglia.infrastructure.internal.memory.*;
import work.ganglia.infrastructure.internal.prompt.StandardPromptEngine;
import work.ganglia.infrastructure.internal.prompt.context.DailyContextSource;
import work.ganglia.infrastructure.internal.skill.DefaultSkillRuntime;
import work.ganglia.infrastructure.internal.skill.DefaultSkillService;
import work.ganglia.infrastructure.internal.skill.FileSystemSkillLoader;
import work.ganglia.infrastructure.internal.skill.JarSkillLoader;
import work.ganglia.infrastructure.internal.state.*;
import work.ganglia.kernel.loop.AgentLoopFactory;
import work.ganglia.kernel.loop.ConsecutiveFailurePolicy;
import work.ganglia.kernel.loop.DefaultObservationDispatcher;
import work.ganglia.kernel.loop.ReActAgentLoop;
import work.ganglia.kernel.subagent.DefaultGraphExecutor;
import work.ganglia.kernel.subagent.GraphExecutor;
import work.ganglia.kernel.task.AgentTaskFactory;
import work.ganglia.kernel.task.DefaultAgentTaskFactory;
import work.ganglia.port.external.llm.ModelGateway;
import work.ganglia.port.internal.hook.AgentInterceptor;
import work.ganglia.kernel.hook.InterceptorPipeline;
import work.ganglia.kernel.hook.builtin.ObservationCompressionHook;
import work.ganglia.port.internal.memory.ContextCompressor;
import work.ganglia.port.internal.memory.DailyRecordManager;
import work.ganglia.port.internal.memory.LongTermMemory;
import work.ganglia.port.internal.memory.MemoryService;
import work.ganglia.port.internal.memory.MemoryStore;
import work.ganglia.port.internal.memory.ObservationCompressor;
import work.ganglia.port.internal.memory.TimelineLedger;
import work.ganglia.port.internal.skill.SkillLoader;
import work.ganglia.port.internal.skill.SkillRuntime;
import work.ganglia.port.internal.skill.SkillService;
import work.ganglia.port.internal.state.SessionManager;
import work.ganglia.util.Constants;
import work.ganglia.util.FileSystemUtil;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * SRP: Orchestrates the initialization and wiring of all Ganglia core components.
 * DIP: Manages the dependency graph and provides a clean entry point.
 */
public class GangliaKernel {
    private static final Logger logger = LoggerFactory.getLogger(GangliaKernel.class);

    private final Vertx vertx;
    private final BootstrapOptions options;
    private final ConfigManager configManager;

    public GangliaKernel(Vertx vertx, BootstrapOptions options) {
        this.vertx = vertx;
        this.options = options;
        this.configManager = options.configPath() != null ? new ConfigManager(vertx, options.configPath()) : new ConfigManager(vertx);
        
        if (options.overrideConfig() != null) {
            configManager.updateConfig(options.overrideConfig());
        }
    }

    public Future<Ganglia> init() {
        final String projectRoot = options.projectRoot() != null ? options.projectRoot() : configManager.getProjectRoot();

        return configManager.init()
            .compose(v -> {
                logger.info("Configuration initialized. Starting self-check...");
                return ensureCoreStructure(projectRoot);
            })
            .compose(v -> initializeSkillSystem(projectRoot))
            .compose(skillService -> initializeMemorySystem(projectRoot).map(longTermMemory -> new InitContext(skillService, longTermMemory)))
            .compose(ctx -> work.ganglia.infrastructure.mcp.McpConfigManager.loadMcpToolSets(vertx, projectRoot)
                .map(mcpRegistry -> new InitContext2(ctx, mcpRegistry)))
            .compose(ctx2 -> assembleSystem(projectRoot, ctx2.ctx.skillService, ctx2.ctx.longTermMemory, ctx2.mcpRegistry));
    }

    private Future<Void> ensureCoreStructure(String projectRoot) {
        List<Future<Void>> dirFutures = new ArrayList<>();
        dirFutures.add(FileSystemUtil.ensureDirectoryExists(vertx, Paths.get(projectRoot, Constants.DIR_SKILLS).toString()));
        dirFutures.add(FileSystemUtil.ensureDirectoryExists(vertx, Paths.get(projectRoot, Constants.DIR_MEMORY).toString()));
        dirFutures.add(FileSystemUtil.ensureDirectoryExists(vertx, Paths.get(projectRoot, Constants.DIR_STATE).toString()));
        dirFutures.add(FileSystemUtil.ensureDirectoryExists(vertx, Paths.get(projectRoot, Constants.DIR_LOGS).toString()));
        dirFutures.add(FileSystemUtil.ensureDirectoryExists(vertx, Paths.get(projectRoot, Constants.DIR_TRACE).toString()));
        return Future.join(dirFutures).map(v -> null);
    }

    private Future<SkillService> initializeSkillSystem(String projectRoot) {
        List<Path> skillPaths = new ArrayList<>();
        skillPaths.add(Paths.get(projectRoot, Constants.DIR_SKILLS));
        String userHome = System.getProperty("user.home");
        if (userHome != null) skillPaths.add(Paths.get(userHome, ".ganglia/skills"));
        skillPaths.add(Paths.get(projectRoot, "skills"));

        List<SkillLoader> loaders = new ArrayList<>();
        loaders.add(new FileSystemSkillLoader(vertx, skillPaths));
        loaders.add(new JarSkillLoader(vertx, skillPaths));

        SkillService skillService = new DefaultSkillService(loaders);
        return skillService.init().map(v -> skillService);
    }

    private Future<LongTermMemory> initializeMemorySystem(String projectRoot) {
        LongTermMemory longTermMemory = new FileSystemLongTermMemory(vertx, Paths.get(projectRoot, Constants.FILE_MEMORY_MD).toString());
        return longTermMemory.ensureInitialized().map(v -> longTermMemory);
    }

    private Future<Ganglia> assembleSystem(String projectRoot, SkillService skillService, LongTermMemory longTermMemory, work.ganglia.infrastructure.mcp.McpRegistry mcpRegistry) {
        ModelGateway rawGateway = options.modelGatewayOverride() != null 
            ? options.modelGatewayOverride() 
            : ModelGatewayFactory.create(vertx, configManager);
        ModelGateway modelGateway = new RetryingModelGateway(rawGateway, vertx);

        MemoryStore memoryStore = new FileSystemMemoryStore(vertx, projectRoot);
        ObservationCompressor observationCompressor = new LLMObservationCompressor(modelGateway, 4000);
        TimelineLedger timelineLedger = new MarkdownTimelineLedger(vertx, projectRoot);

        SkillRuntime skillRuntime = new DefaultSkillRuntime(vertx, skillService);
        TokenCounter tokenCounter = new TokenCounter();
        ContextCompressor compressor = new DefaultContextCompressor(modelGateway, configManager);
        DailyRecordManager dailyRecordManager = new FileSystemDailyRecordManager(vertx, Paths.get(projectRoot, Constants.DIR_MEMORY).toString());

        MemoryService memoryService = new MemoryService(vertx);
        memoryService.registerModule(new DailyJournalModule(compressor, dailyRecordManager));
        memoryService.registerModule(new LongTermKnowledgeModule(longTermMemory));

        InterceptorPipeline pipeline = new InterceptorPipeline();
        pipeline.addInterceptor(new ObservationCompressionHook(observationCompressor, memoryStore));

        ToolsFactory toolsFactory = new ToolsFactory(vertx, compressor, longTermMemory, projectRoot);
        StandardPromptEngine promptEngine = new StandardPromptEngine(vertx, memoryService, skillRuntime, null, tokenCounter, options.extraContextSources());
        promptEngine.addContextSource(new DailyContextSource(vertx, Paths.get(projectRoot, Constants.DIR_MEMORY).toString()));
        promptEngine.addContextSource(new MemoryContextSource(memoryStore));

        SessionManager sessionManager = new DefaultSessionManager(new FileStateEngine(vertx), new FileLogManager(vertx), configManager);
        
        List<ToolSet> allExtraToolSets = new ArrayList<>(options.extraToolSets());
        if (mcpRegistry != null && mcpRegistry.toolSets() != null) {
            allExtraToolSets.addAll(mcpRegistry.toolSets());
        }
        for (ToolSetProvider provider : options.extraToolSetProviders()) {
            allExtraToolSets.add(provider.create(vertx, compressor, longTermMemory, projectRoot));
        }
        allExtraToolSets.add(new work.ganglia.infrastructure.external.tool.RecallMemoryTools(memoryStore));
        
        DefaultToolExecutor toolExecutor = new DefaultToolExecutor(toolsFactory, allExtraToolSets);
        DefaultObservationDispatcher dispatcher = new DefaultObservationDispatcher(vertx);
        ConsecutiveFailurePolicy failurePolicy = new ConsecutiveFailurePolicy();
        DefaultContextOptimizer contextOptimizer = new DefaultContextOptimizer(configManager, configManager, compressor, tokenCounter);

        // Core component construction via explicitly declared dependencies and Factories
        LazyTaskFactoryProxy lazyTaskFactoryProxy = new LazyTaskFactoryProxy();
        AgentLoopFactory loopFactory = () -> ReActAgentLoop.builder()
            .vertx(vertx)
            .dispatcher(dispatcher)
            .sessionManager(sessionManager)
            .configProvider(configManager)
            .contextOptimizer(contextOptimizer)
            .promptEngine(promptEngine)
            .modelGateway(modelGateway)
            .taskFactory(lazyTaskFactoryProxy)
            .faultTolerancePolicy(failurePolicy)
            .pipeline(pipeline)
            .build();

        GraphExecutor graphExecutor = new DefaultGraphExecutor(loopFactory);

        AgentTaskFactory taskFactory = new DefaultAgentTaskFactory(
            loopFactory, toolExecutor, graphExecutor, skillService, skillRuntime
        );
        
        // Wire circular dependencies
        lazyTaskFactoryProxy.setDelegate(taskFactory);
        // Better: use AgentEnv as the holder or just set it in PromptEngine
        promptEngine.setTaskFactory(taskFactory);
        graphExecutor.initialize(taskFactory);

        // Build AgentEnv as the global container/registry
        AgentEnv env = AgentEnv.builder()
            .vertx(vertx)
            .modelGateway(modelGateway)
            .sessionManager(sessionManager)
            .promptEngine(promptEngine)
            .configProvider(configManager)
            .modelConfig(configManager)
            .compressor(compressor)
            .memoryService(memoryService)
            .dispatcher(dispatcher)
            .faultTolerancePolicy(failurePolicy)
            .contextOptimizer(contextOptimizer)
            .taskFactory(taskFactory)
            .build();

        // Update the loop factory to correctly provide the task factory without reflection hack:
        AgentLoopFactory finalLoopFactory = () -> ReActAgentLoop.builder()
            .vertx(vertx)
            .dispatcher(dispatcher)
            .sessionManager(sessionManager)
            .configProvider(configManager)
            .contextOptimizer(contextOptimizer)
            .promptEngine(promptEngine)
            .modelGateway(modelGateway)
            .taskFactory(taskFactory)
            .faultTolerancePolicy(failurePolicy)
            .pipeline(pipeline)
            .build();

        // Update factories with the correct loop factory
        AgentTaskFactory finalTaskFactory = new DefaultAgentTaskFactory(
            finalLoopFactory, toolExecutor, graphExecutor, skillService, skillRuntime
        );
        env.setTaskFactory(finalTaskFactory);
        promptEngine.setTaskFactory(finalTaskFactory);
        
        // recreate GraphExecutor to use finalLoopFactory
        GraphExecutor finalGraphExecutor = new DefaultGraphExecutor(finalLoopFactory);

        new TraceManager(vertx, configManager);
        new TokenUsageManager(vertx, tokenCounter);

        ReActAgentLoop primaryAgentLoop = (ReActAgentLoop) finalLoopFactory.createLoop();

        int mcpCount = mcpRegistry != null && mcpRegistry.toolSets() != null ? mcpRegistry.toolSets().size() : 0;
        return Future.succeededFuture(new Ganglia(vertx, modelGateway, toolExecutor, sessionManager, primaryAgentLoop, configManager, env, mcpCount, mcpRegistry));
    }

    private record InitContext(SkillService skillService, LongTermMemory longTermMemory) {}
    private record InitContext2(InitContext ctx, work.ganglia.infrastructure.mcp.McpRegistry mcpRegistry) {}

    private static class LazyTaskFactoryProxy implements AgentTaskFactory {
        private AgentTaskFactory delegate;

        public void setDelegate(AgentTaskFactory delegate) {
            this.delegate = delegate;
        }

        @Override
        public work.ganglia.kernel.task.AgentTask create(work.ganglia.port.external.tool.ToolCall call, work.ganglia.port.chat.SessionContext context) {
            return delegate.create(call, context);
        }

        @Override
        public List<work.ganglia.port.external.tool.ToolDefinition> getAvailableDefinitions(work.ganglia.port.chat.SessionContext context) {
            return delegate.getAvailableDefinitions(context);
        }
    }
}
