package me.stream;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import me.stream.ganglia.core.Ganglia;
import me.stream.ganglia.core.config.ConfigManager;
import me.stream.ganglia.core.llm.ModelGateway;
import me.stream.ganglia.core.llm.ModelGatewayFactory;
import me.stream.ganglia.core.loop.ReActAgentLoop;
import me.stream.ganglia.core.prompt.StandardPromptEngine;
import me.stream.ganglia.core.session.DefaultSessionManager;
import me.stream.ganglia.core.session.SessionManager;
import me.stream.ganglia.core.state.FileLogManager;
import me.stream.ganglia.core.state.FileStateEngine;
import me.stream.ganglia.core.state.TraceManager;
import me.stream.ganglia.memory.ContextCompressor;
import me.stream.ganglia.memory.DailyRecordManager;
import me.stream.ganglia.memory.KnowledgeBase;
import me.stream.ganglia.memory.MemoryService;
import me.stream.ganglia.memory.TokenCounter;
import me.stream.ganglia.core.state.TokenUsageManager;
import me.stream.ganglia.skills.SkillPromptInjector;
import me.stream.ganglia.skills.SkillRegistry;
import me.stream.ganglia.skills.SkillSuggester;
import me.stream.ganglia.tools.DefaultToolExecutor;
import me.stream.ganglia.tools.ToolsFactory;
import me.stream.ganglia.core.prompt.context.DailyContextSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

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
            ModelGateway modelGateway = modelGatewayOverride != null ? modelGatewayOverride : ModelGatewayFactory.create(vertx, configManager);

            // 1. Setup Skill System
            List<Path> skillPaths = new ArrayList<>();
            skillPaths.add(Paths.get(".ganglia/skills"));
            String userHome = System.getProperty("user.home");
            if (userHome != null) {
                skillPaths.add(Paths.get(userHome, ".ganglia/skills"));
            }
            skillPaths.add(Paths.get("skills"));

            SkillRegistry skillRegistry = new SkillRegistry(vertx, skillPaths);

            return skillRegistry.init().map(v2 -> {
                // 2. Setup Kernel & Memory
                TokenCounter tokenCounter = new TokenCounter();
                KnowledgeBase knowledgeBase = new KnowledgeBase(vertx);
                ContextCompressor compressor = new ContextCompressor(modelGateway, configManager);
                DailyRecordManager dailyRecordManager = new DailyRecordManager(vertx, ".ganglia/memory");

                ToolsFactory toolsFactory = new ToolsFactory(vertx, compressor, knowledgeBase);
                DefaultToolExecutor toolExecutor = new DefaultToolExecutor(toolsFactory, skillRegistry);

                SkillPromptInjector skillInjector = new SkillPromptInjector(vertx, skillRegistry);
                SkillSuggester skillSuggester = new SkillSuggester(vertx, skillRegistry);
                StandardPromptEngine promptEngine = new StandardPromptEngine(vertx, knowledgeBase, skillInjector, skillSuggester, toolExecutor, tokenCounter);
                
                // Add Daily Source
                promptEngine.addContextSource(new DailyContextSource(vertx, ".ganglia/memory"));

                FileStateEngine stateEngine = new FileStateEngine(vertx);
                FileLogManager logManager = new FileLogManager(vertx);

                // 3. Setup Observability & Usage
                new TraceManager(vertx, configManager);
                new TokenUsageManager(vertx, tokenCounter);
                new MemoryService(vertx, compressor, dailyRecordManager);

                SessionManager sessionManager = new DefaultSessionManager(stateEngine, logManager, configManager);

                ReActAgentLoop agentLoop = new ReActAgentLoop(vertx, modelGateway, toolExecutor, sessionManager, 
                    promptEngine, configManager);

                return new Ganglia(modelGateway, toolExecutor, sessionManager, agentLoop);
            });
        });
    }

    public static void main(String[] args) {
        System.out.println("Ganglia Core Bootstrapper. Use me.stream.example.GangliaExample to run the CLI.");
    }
}
