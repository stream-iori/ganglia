package work.ganglia.kernel;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.ganglia.BootstrapOptions;
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
import work.ganglia.kernel.loop.ConsecutiveFailurePolicy;
import work.ganglia.kernel.loop.DefaultObservationDispatcher;
import work.ganglia.kernel.loop.ReActAgentLoop;
import work.ganglia.kernel.subagent.DefaultGraphExecutor;
import work.ganglia.kernel.subagent.GraphExecutor;
import work.ganglia.kernel.task.AgentTaskFactory;
import work.ganglia.kernel.task.DefaultAgentTaskFactory;
import work.ganglia.port.external.llm.ModelGateway;
import work.ganglia.port.internal.memory.ContextCompressor;
import work.ganglia.port.internal.memory.DailyRecordManager;
import work.ganglia.port.internal.memory.LongTermMemory;
import work.ganglia.port.internal.memory.MemoryService;
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

        return configManager.init().compose(v -> {
            logger.info("Configuration initialized. Starting self-check...");
            return ensureCoreStructure(projectRoot);
        }).compose(v -> {
            ModelConfigProvider modelConfig = configManager;
            AgentConfigProvider agentConfig = configManager;

            ModelGateway rawGateway = options.modelGatewayOverride() != null 
                ? options.modelGatewayOverride() 
                : ModelGatewayFactory.create(vertx, modelConfig);
            ModelGateway modelGateway = new RetryingModelGateway(rawGateway, vertx);

            SkillService skillService = setupSkillService(projectRoot);
            SkillRuntime skillRuntime = new DefaultSkillRuntime(vertx, skillService);

            return skillService.init().compose(v2 -> {
                LongTermMemory longTermMemory = new FileSystemLongTermMemory(vertx, Paths.get(projectRoot, Constants.FILE_MEMORY_MD).toString());
                
                return longTermMemory.ensureInitialized().compose(v3 -> {
                    TokenCounter tokenCounter = new TokenCounter();
                    ContextCompressor compressor = new DefaultContextCompressor(modelGateway, modelConfig);
                    DailyRecordManager dailyRecordManager = new FileSystemDailyRecordManager(vertx, Paths.get(projectRoot, Constants.DIR_MEMORY).toString());

                    MemoryService memoryService = new MemoryService(vertx);
                    memoryService.registerModule(new DailyJournalModule(compressor, dailyRecordManager));
                    memoryService.registerModule(new LongTermKnowledgeModule(longTermMemory));

                    ToolsFactory toolsFactory = new ToolsFactory(vertx, compressor, longTermMemory, projectRoot);
                    StandardPromptEngine promptEngine = new StandardPromptEngine(vertx, memoryService, skillRuntime, null, tokenCounter, options.extraContextSources());
                    promptEngine.addContextSource(new DailyContextSource(vertx, Paths.get(projectRoot, Constants.DIR_MEMORY).toString()));

                    SessionManager sessionManager = new DefaultSessionManager(new FileStateEngine(vertx), new FileLogManager(vertx), configManager);
                    DefaultToolExecutor toolExecutor = new DefaultToolExecutor(toolsFactory, options.extraToolSets());
                    DefaultObservationDispatcher dispatcher = new DefaultObservationDispatcher(vertx);

                    // Create AgentEnv
                    AgentEnv env = new AgentEnv(
                        vertx, modelGateway, sessionManager, promptEngine,
                        agentConfig, modelConfig, compressor, memoryService,
                        dispatcher, new ConsecutiveFailurePolicy(),
                        new DefaultContextOptimizer(modelConfig, agentConfig, compressor, tokenCounter)
                    );

                    GraphExecutor graphExecutor = new DefaultGraphExecutor(env);

                    AgentTaskFactory taskFactory = new DefaultAgentTaskFactory(
                        env, toolExecutor, graphExecutor, skillService, skillRuntime
                    );
                    
                    env.setTaskFactory(taskFactory);
                    promptEngine.setTaskFactory(taskFactory);
                    graphExecutor.initialize(taskFactory);

                    new TraceManager(vertx, configManager);
                    new TokenUsageManager(vertx, tokenCounter);

                    ReActAgentLoop agentLoop = new ReActAgentLoop(env);

                    return Future.succeededFuture(new Ganglia(modelGateway, toolExecutor, sessionManager, agentLoop, configManager, env));
                });
            });
        });
    }

    private Future<Void> ensureCoreStructure(String projectRoot) {
        List<Future<Void>> dirFutures = new ArrayList<>();
        dirFutures.add(FileSystemUtil.ensureDirectoryExists(vertx, Paths.get(projectRoot, Constants.DIR_SKILLS).toString()));
        dirFutures.add(FileSystemUtil.ensureDirectoryExists(vertx, Paths.get(projectRoot, Constants.DIR_MEMORY).toString()));
        dirFutures.add(FileSystemUtil.ensureDirectoryExists(vertx, Paths.get(projectRoot, Constants.DIR_STATE).toString()));
        dirFutures.add(FileSystemUtil.ensureDirectoryExists(vertx, Paths.get(projectRoot, Constants.DIR_LOGS).toString()));
        dirFutures.add(FileSystemUtil.ensureDirectoryExists(vertx, Paths.get(projectRoot, Constants.DIR_TRACE).toString()));
        return Future.join(dirFutures).map(v2 -> null);
    }

    private SkillService setupSkillService(String projectRoot) {
        List<Path> skillPaths = new ArrayList<>();
        skillPaths.add(Paths.get(projectRoot, Constants.DIR_SKILLS));
        String userHome = System.getProperty("user.home");
        if (userHome != null) skillPaths.add(Paths.get(userHome, ".ganglia/skills"));
        skillPaths.add(Paths.get(projectRoot, "skills"));

        List<SkillLoader> loaders = new ArrayList<>();
        loaders.add(new FileSystemSkillLoader(vertx, skillPaths));
        loaders.add(new JarSkillLoader(vertx, skillPaths));

        return new DefaultSkillService(loaders);
    }
}
