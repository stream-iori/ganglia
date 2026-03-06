package work;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import work.ganglia.core.Ganglia;
import work.ganglia.core.config.ConfigManager;
import work.ganglia.core.llm.ModelGateway;
import work.ganglia.core.llm.ModelGatewayFactory;
import work.ganglia.core.llm.RetryingModelGateway;
import work.ganglia.core.loop.ConsecutiveFailurePolicy;
import work.ganglia.core.loop.EventBusObservationPublisher;
import work.ganglia.core.loop.StandardAgentLoop;
import java.util.List;
import work.ganglia.core.prompt.StandardPromptEngine;
import work.ganglia.core.schedule.DefaultSchedulableFactory;
import work.ganglia.core.schedule.SchedulableFactory;
import work.ganglia.core.session.DefaultContextOptimizer;
import work.ganglia.core.session.DefaultSessionManager;
import work.ganglia.core.session.SessionManager;
import work.ganglia.core.state.FileLogManager;
import work.ganglia.core.state.FileStateEngine;
import work.ganglia.core.state.TraceManager;
import work.ganglia.memory.ContextCompressor;
import work.ganglia.memory.DailyRecordManager;
import work.ganglia.memory.FileSystemDailyRecordManager;
import work.ganglia.memory.FileSystemKnowledgeBase;
import work.ganglia.memory.KnowledgeBase;
import work.ganglia.memory.MemoryService;
import work.ganglia.memory.TokenCounter;
import work.ganglia.core.state.TokenUsageManager;
import work.ganglia.skills.*;
import work.ganglia.tools.DefaultToolExecutor;
import work.ganglia.tools.ToolsFactory;
import work.ganglia.tools.subagent.DefaultGraphExecutor;
import work.ganglia.tools.subagent.GraphExecutor;
import work.ganglia.core.prompt.context.DailyContextSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import work.ganglia.core.config.model.GangliaConfig;
import work.ganglia.core.webui.WebUIEventPublisher;
import work.ganglia.core.webui.WebUIVerticle;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    /**
     * Bootstraps the Ganglia core components.
     *
     * @param vertx The Vert.x instance.
     * @return An initialized Ganglia instance.
     */
    public static Future<Ganglia> bootstrap(Vertx vertx) {
        return bootstrap(vertx, null);
    }

    /**
     * Bootstraps the Ganglia core components with a custom config path.
     *
     * @param vertx      The Vert.x instance.
     * @param configPath Custom path to config.json.
     * @return An initialized Ganglia instance.
     */
    public static Future<Ganglia> bootstrap(Vertx vertx, String configPath) {
        return bootstrap(vertx, configPath, null);
    }

    /**
     * Bootstraps the Ganglia core components with a custom config path and/or override config.
     *
     * @param vertx         The Vert.x instance.
     * @param configPath    Custom path to config.json (can be null).
     * @param overrideConfig Optional JsonObject to override configuration (can be null).
     * @return An initialized Ganglia instance.
     */
    public static Future<Ganglia> bootstrap(Vertx vertx, String configPath, JsonObject overrideConfig) {
        return bootstrap(vertx, configPath, overrideConfig, null);
    }

    /**
     * Bootstraps the Ganglia core components with full control over configuration and model gateway.
     *
     * @param vertx                The Vert.x instance.
     * @param configPath           Custom path to config.json.
     * @param overrideConfig       Optional JsonObject to override configuration.
     * @param modelGatewayOverride Optional ModelGateway implementation to use instead of the factory-created one.
     * @return An initialized Ganglia instance.
     */
    public static Future<Ganglia> bootstrap(Vertx vertx, String configPath, JsonObject overrideConfig, ModelGateway modelGatewayOverride) {
        ConfigManager configManager = configPath != null ? new ConfigManager(vertx, configPath) : new ConfigManager(vertx);

        if (overrideConfig != null) {
            configManager.updateConfig(overrideConfig);
        }

        return configManager.init().compose(v -> {
            ModelGateway rawGateway = modelGatewayOverride != null ? modelGatewayOverride : ModelGatewayFactory.create(vertx, configManager);
            ModelGateway modelGateway = new RetryingModelGateway(rawGateway, vertx);

            // 1. Setup Skill System
            List<Path> skillPaths = new ArrayList<>();
            skillPaths.add(Paths.get(".ganglia/skills"));
            String userHome = System.getProperty("user.home");
            if (userHome != null) {
                skillPaths.add(Paths.get(userHome, ".ganglia/skills"));
            }
            skillPaths.add(Paths.get("skills"));

            // Resolve loaders from config
            List<SkillLoader> loaders = new ArrayList<>();
            JsonObject skillConfig = configManager.getConfig().getJsonObject("skills");
            List<String> loaderTypes = (skillConfig != null && skillConfig.containsKey("loaders"))
                ? skillConfig.getJsonArray("loaders").getList()
                : List.of("filesystem", "jar");

            if (loaderTypes.contains("filesystem")) {
                loaders.add(new FileSystemSkillLoader(vertx, skillPaths));
            }
            if (loaderTypes.contains("jar")) {
                loaders.add(new JarSkillLoader(vertx, skillPaths));
            }

            SkillService skillService = new DefaultSkillService(loaders);
            SkillRuntime skillRuntime = new DefaultSkillRuntime(vertx, skillService);

            return skillService.init().map(v2 -> {
                // 2. Setup Kernel & Memory
                TokenCounter tokenCounter = new TokenCounter();
                KnowledgeBase knowledgeBase = new FileSystemKnowledgeBase(vertx);
                ContextCompressor compressor = new ContextCompressor(modelGateway, configManager);
                DailyRecordManager dailyRecordManager = new FileSystemDailyRecordManager(vertx, ".ganglia/memory");

                MemoryService memoryService = new MemoryService(vertx);
                memoryService.registerModule(new work.ganglia.memory.DailyJournalModule(compressor, dailyRecordManager));
                memoryService.registerModule(new work.ganglia.memory.LongTermKnowledgeModule(knowledgeBase));

                ToolsFactory toolsFactory = new ToolsFactory(vertx, compressor, knowledgeBase, configManager.getProjectRoot());

                // Initialize PromptEngine
                StandardPromptEngine promptEngine = new StandardPromptEngine(vertx, memoryService, skillRuntime, null, tokenCounter);

                // Initialize SessionManager
                FileStateEngine stateEngine = new FileStateEngine(vertx);
                FileLogManager logManager = new FileLogManager(vertx);
                SessionManager sessionManager = new DefaultSessionManager(stateEngine, logManager, configManager);

                // Initialize ToolExecutor
                DefaultToolExecutor toolExecutor = new DefaultToolExecutor(toolsFactory);

                // Initialize GraphExecutor
                GraphExecutor graphExecutor = new DefaultGraphExecutor(vertx, modelGateway, sessionManager, promptEngine, configManager, compressor);

                // Initialize SchedulableFactory
                SchedulableFactory scheduleableFactory = new DefaultSchedulableFactory(
                    vertx, modelGateway, sessionManager, promptEngine, configManager, compressor,
                    toolExecutor, graphExecutor, skillService, skillRuntime
                );

                // Wire SchedulableFactory back into PromptEngine and GraphExecutor
                promptEngine.setSchedulableFactory(scheduleableFactory);
                if (graphExecutor instanceof DefaultGraphExecutor) {
                    ((DefaultGraphExecutor) graphExecutor).setSchedulableFactory(scheduleableFactory);
                }

                // Add Daily Source
                promptEngine.addContextSource(new DailyContextSource(vertx, ".ganglia/memory"));

                // 3. Setup Observability & Usage
                new TraceManager(vertx, configManager);
                new TokenUsageManager(vertx, tokenCounter);

                StandardAgentLoop agentLoop = new StandardAgentLoop(vertx, modelGateway, scheduleableFactory, sessionManager,
                    promptEngine, configManager, new DefaultContextOptimizer(configManager, compressor, tokenCounter),
                    new ConsecutiveFailurePolicy(), List.of(
                        new EventBusObservationPublisher(vertx),
                        new WebUIEventPublisher(vertx)
                    ));

                // 4. Start WebUI Verticle if enabled
                GangliaConfig.WebUIConfig webUIConfig = configManager.getGangliaConfig().webui();
                if (webUIConfig == null || webUIConfig.enabled()) {
                    int port = webUIConfig != null ? webUIConfig.port() : 8080;
                    String webroot = webUIConfig != null ? webUIConfig.webroot() : "webroot";
                    vertx.deployVerticle(new WebUIVerticle(port, webroot, agentLoop, sessionManager));
                }

                return new Ganglia(modelGateway, toolExecutor, sessionManager, agentLoop, configManager);
            });
        });
    }

    public static void main(String[] args) {
        System.out.println("Ganglia Core Bootstrapper. Use me.stream.example.GangliaExample to run the CLI.");
    }
}
