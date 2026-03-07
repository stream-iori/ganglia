package work;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import work.ganglia.Ganglia;
import work.ganglia.BootstrapOptions;
import work.ganglia.config.ConfigManager;
import work.ganglia.port.external.llm.ModelGateway;
import work.ganglia.port.internal.skill.SkillService;
import work.ganglia.infrastructure.external.llm.ModelGatewayFactory;
import work.ganglia.infrastructure.external.llm.RetryingModelGateway;
import work.ganglia.kernel.loop.ConsecutiveFailurePolicy;
import work.ganglia.kernel.loop.EventBusObservationPublisher;
import work.ganglia.kernel.loop.StandardAgentLoop;
import work.ganglia.kernel.loop.AgentLoopObserver;
import java.util.List;
import work.ganglia.infrastructure.internal.prompt.StandardPromptEngine;
import work.ganglia.kernel.task.DefaultSchedulableFactory;
import work.ganglia.kernel.task.SchedulableFactory;
import work.ganglia.infrastructure.internal.state.DefaultContextOptimizer;
import work.ganglia.infrastructure.internal.state.DefaultSessionManager;
import work.ganglia.port.internal.state.SessionManager;
import work.ganglia.infrastructure.internal.state.FileLogManager;
import work.ganglia.infrastructure.internal.state.FileStateEngine;
import work.ganglia.infrastructure.internal.state.TraceManager;
import work.ganglia.port.internal.memory.ContextCompressor;
import work.ganglia.infrastructure.internal.memory.DefaultContextCompressor;
import work.ganglia.port.internal.memory.DailyRecordManager;
import work.ganglia.infrastructure.internal.memory.FileSystemDailyRecordManager;
import work.ganglia.infrastructure.internal.memory.FileSystemKnowledgeBase;
import work.ganglia.port.internal.memory.KnowledgeBase;
import work.ganglia.port.internal.memory.MemoryService;
import work.ganglia.infrastructure.internal.memory.TokenCounter;
import work.ganglia.infrastructure.internal.state.TokenUsageManager;
import work.ganglia.infrastructure.internal.skill.*;
import work.ganglia.port.internal.skill.*;
import work.ganglia.infrastructure.external.tool.DefaultToolExecutor;
import work.ganglia.infrastructure.external.tool.ToolsFactory;
import work.ganglia.kernel.subagent.DefaultGraphExecutor;
import work.ganglia.kernel.subagent.GraphExecutor;
import work.ganglia.infrastructure.internal.prompt.context.DailyContextSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import work.ganglia.util.Constants;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static Future<Ganglia> bootstrap(Vertx vertx) {
        return bootstrap(vertx, BootstrapOptions.defaultOptions());
    }

    public static Future<Ganglia> bootstrap(Vertx vertx, String configPath) {
        return bootstrap(vertx, BootstrapOptions.defaultOptions().withConfigPath(configPath));
    }

    public static Future<Ganglia> bootstrap(Vertx vertx, String configPath, JsonObject overrideConfig) {
        return bootstrap(vertx, BootstrapOptions.defaultOptions().withConfigPath(configPath).withOverrideConfig(overrideConfig));
    }

    /**
     * Backward compatibility overload for integration tests.
     */
    public static Future<Ganglia> bootstrap(Vertx vertx, String configPath, JsonObject overrideConfig, ModelGateway modelGatewayOverride) {
        return bootstrap(vertx, BootstrapOptions.defaultOptions()
            .withConfigPath(configPath)
            .withOverrideConfig(overrideConfig)
            .withModelGateway(modelGatewayOverride));
    }

    /**
     * Backward compatibility overload for integration tests.
     */
    public static Future<Ganglia> bootstrap(Vertx vertx, String configPath, JsonObject overrideConfig, List<AgentLoopObserver> extraObservers) {
        return bootstrap(vertx, BootstrapOptions.defaultOptions()
            .withConfigPath(configPath)
            .withOverrideConfig(overrideConfig)
            .withObservers(extraObservers));
    }

    public static Future<Ganglia> bootstrap(Vertx vertx, BootstrapOptions options) {
        ConfigManager configManager = options.configPath() != null ? new ConfigManager(vertx, options.configPath()) : new ConfigManager(vertx);

        if (options.overrideConfig() != null) {
            configManager.updateConfig(options.overrideConfig());
        }

        return configManager.init().compose(v -> {
            ModelGateway rawGateway = options.modelGatewayOverride() != null ? options.modelGatewayOverride() : ModelGatewayFactory.create(vertx, configManager);
            ModelGateway modelGateway = new RetryingModelGateway(rawGateway, vertx);

            // Setup loaders
            List<Path> skillPaths = new ArrayList<>();
            skillPaths.add(Paths.get(Constants.DIR_SKILLS));
            String userHome = System.getProperty("user.home");
            if (userHome != null) skillPaths.add(Paths.get(userHome, ".ganglia/skills"));
            skillPaths.add(Paths.get("skills"));

            List<SkillLoader> loaders = new ArrayList<>();
            loaders.add(new FileSystemSkillLoader(vertx, skillPaths));
            loaders.add(new JarSkillLoader(vertx, skillPaths));

            SkillService skillService = new DefaultSkillService(loaders);
            SkillRuntime skillRuntime = new DefaultSkillRuntime(vertx, skillService);

            return skillService.init().map(v2 -> {
                TokenCounter tokenCounter = new TokenCounter();
                KnowledgeBase knowledgeBase = new FileSystemKnowledgeBase(vertx);
                ContextCompressor compressor = new DefaultContextCompressor(modelGateway, configManager);
                DailyRecordManager dailyRecordManager = new FileSystemDailyRecordManager(vertx, Constants.DIR_MEMORY);

                MemoryService memoryService = new MemoryService(vertx);
                memoryService.registerModule(new work.ganglia.infrastructure.internal.memory.DailyJournalModule(compressor, dailyRecordManager));
                memoryService.registerModule(new work.ganglia.infrastructure.internal.memory.LongTermKnowledgeModule(knowledgeBase));

                ToolsFactory toolsFactory = new ToolsFactory(vertx, compressor, knowledgeBase, configManager.getProjectRoot());
                StandardPromptEngine promptEngine = new StandardPromptEngine(vertx, memoryService, skillRuntime, null, tokenCounter);
                promptEngine.addContextSource(new DailyContextSource(vertx, Constants.DIR_MEMORY));

                SessionManager sessionManager = new DefaultSessionManager(new FileStateEngine(vertx), new FileLogManager(vertx), configManager);
                DefaultToolExecutor toolExecutor = new DefaultToolExecutor(toolsFactory);
                GraphExecutor graphExecutor = new DefaultGraphExecutor(vertx, modelGateway, sessionManager, promptEngine, configManager, compressor);

                SchedulableFactory scheduleableFactory = new DefaultSchedulableFactory(
                    vertx, modelGateway, sessionManager, promptEngine, configManager, compressor,
                    toolExecutor, graphExecutor, skillService, skillRuntime
                );
                promptEngine.setSchedulableFactory(scheduleableFactory);
                if (graphExecutor instanceof DefaultGraphExecutor) {
                    ((DefaultGraphExecutor) graphExecutor).setSchedulableFactory(scheduleableFactory);
                }

                new TraceManager(vertx, configManager);
                new TokenUsageManager(vertx, tokenCounter);

                List<AgentLoopObserver> observers = new ArrayList<>();
                observers.add(new EventBusObservationPublisher(vertx));
                if (options.extraObservers() != null) observers.addAll(options.extraObservers());

                StandardAgentLoop agentLoop = new StandardAgentLoop(vertx, modelGateway, scheduleableFactory, sessionManager,
                    promptEngine, configManager, new DefaultContextOptimizer(configManager, compressor, tokenCounter),
                    new ConsecutiveFailurePolicy(), observers);

                return new Ganglia(modelGateway, toolExecutor, sessionManager, agentLoop, configManager);
            });
        });
    }

    public static void main(String[] args) {
        System.out.println("Ganglia Core Bootstrapper. Start with ganglia-terminal or ganglia-web modules for UI.");
    }
}
