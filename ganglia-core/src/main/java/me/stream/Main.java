package me.stream;

import io.vertx.core.Vertx;
import me.stream.ganglia.core.llm.OpenAIModelGateway;
import me.stream.ganglia.core.loop.ReActAgentLoop;
import me.stream.ganglia.memory.ContextCompressor;
import me.stream.ganglia.memory.KnowledgeBase;
import me.stream.ganglia.core.Ganglia;
import me.stream.ganglia.core.prompt.StandardPromptEngine;
import me.stream.ganglia.core.session.DefaultSessionManager;
import me.stream.ganglia.core.session.SessionManager;
import me.stream.ganglia.skills.SkillPromptInjector;
import me.stream.ganglia.skills.SkillRegistry;
import me.stream.ganglia.skills.SkillSuggester;
import me.stream.ganglia.core.state.FileLogManager;
import me.stream.ganglia.core.state.FileStateEngine;
import me.stream.ganglia.tools.DefaultToolExecutor;
import me.stream.ganglia.tools.ToolsFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    /**
     * Bootstraps the Ganglia core components.
     * @param vertx The Vert.x instance.
     * @return An initialized Ganglia instance.
     */
    public static Ganglia bootstrap(Vertx vertx) {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null) {
            apiKey = System.getenv("MOONSHOT_API_KEY");
        }

        if (apiKey == null) {
            logger.error("API key not found. Please set OPENAI_API_KEY or MOONSHOT_API_KEY.");
            return null;
        }

        String baseUrl = System.getenv("OPENAI_BASE_URL");
        if (baseUrl == null) {
            baseUrl = "https://api.openai.com/v1";
        }

        // 1. Setup Kernel
        OpenAIModelGateway modelGateway = new OpenAIModelGateway(vertx, apiKey, baseUrl);
        KnowledgeBase knowledgeBase = new KnowledgeBase(vertx);
        ContextCompressor compressor = new ContextCompressor(modelGateway);
        SkillRegistry skillRegistry = new SkillRegistry(vertx, Paths.get("skills"));

        ToolsFactory toolsFactory = new ToolsFactory(vertx, compressor, knowledgeBase);
        DefaultToolExecutor toolExecutor = new DefaultToolExecutor(toolsFactory, skillRegistry);

        SkillPromptInjector skillInjector = new SkillPromptInjector(vertx, skillRegistry, Paths.get("skills"));
        SkillSuggester skillSuggester = new SkillSuggester(vertx, skillRegistry);
        StandardPromptEngine promptEngine = new StandardPromptEngine(vertx, knowledgeBase, skillInjector, skillSuggester);

        FileStateEngine stateEngine = new FileStateEngine(vertx);
        FileLogManager logManager = new FileLogManager(vertx);

        SessionManager sessionManager = new DefaultSessionManager(stateEngine, logManager);

        ReActAgentLoop agentLoop = new ReActAgentLoop(modelGateway, toolExecutor, sessionManager, promptEngine, 10);

        return new Ganglia(modelGateway, toolExecutor, sessionManager, agentLoop);
    }

    public static void main(String[] args) {
        System.out.println("Ganglia Core Bootstrapper. Use me.stream.example.GangliaExample to run the CLI.");
    }
}