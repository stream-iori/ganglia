package me.stream;

import io.vertx.core.Vertx;
import me.stream.ganglia.core.llm.OpenAIModelGateway;
import me.stream.ganglia.core.loop.ReActAgentLoop;
import me.stream.ganglia.core.memory.ContextCompressor;
import me.stream.ganglia.core.memory.KnowledgeBase;
import me.stream.ganglia.core.model.ModelOptions;
import me.stream.ganglia.core.model.SessionContext;
import me.stream.ganglia.core.model.ToDoList;
import me.stream.ganglia.core.prompt.StandardPromptEngine;
import me.stream.ganglia.core.skills.SkillPromptInjector;
import me.stream.ganglia.core.skills.SkillRegistry;
import me.stream.ganglia.core.skills.SkillSuggester;
import me.stream.ganglia.core.state.FileLogManager;
import me.stream.ganglia.core.state.FileStateEngine;
import me.stream.ganglia.core.tools.DefaultToolExecutor;
import me.stream.ganglia.core.tools.ToolsFactory;
import me.stream.ganglia.core.ui.TerminalUI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.util.Collections;
import java.util.UUID;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null) {
            apiKey = System.getenv("MOONSHOT_API_KEY"); // Fallback for IT tests style
        }
        
        if (apiKey == null) {
            System.err.println("API key not found. Please set OPENAI_API_KEY or MOONSHOT_API_KEY.");
            return;
        }

        String baseUrl = System.getenv("OPENAI_BASE_URL");
        if (baseUrl == null) {
            baseUrl = "https://api.openai.com/v1";
        }

        Vertx vertx = Vertx.vertx();
        
        // 1. Setup Kernel
        OpenAIModelGateway modelGateway = new OpenAIModelGateway(vertx, apiKey, baseUrl);
        KnowledgeBase knowledgeBase = new KnowledgeBase(vertx);
        ContextCompressor compressor = new ContextCompressor(modelGateway);
        SkillRegistry skillRegistry = new SkillRegistry(vertx, Paths.get("skills"));
        
        ToolsFactory toolsFactory = new ToolsFactory(vertx, compressor, knowledgeBase);
        DefaultToolExecutor toolExecutor = new DefaultToolExecutor(toolsFactory, skillRegistry);
        
        SkillPromptInjector skillInjector = new SkillPromptInjector(vertx, skillRegistry, Paths.get("skills"));
        SkillSuggester skillSuggester = new SkillSuggester(vertx, skillRegistry);
        StandardPromptEngine promptEngine = new StandardPromptEngine(knowledgeBase, skillInjector, skillSuggester);
        
        FileStateEngine stateEngine = new FileStateEngine(vertx);
        FileLogManager logManager = new FileLogManager(vertx);
        
        ReActAgentLoop agentLoop = new ReActAgentLoop(modelGateway, toolExecutor, stateEngine, logManager, promptEngine, 10);
        
        // 2. Setup UI
        TerminalUI ui = new TerminalUI(vertx);
        
        String sessionId = UUID.randomUUID().toString();
        SessionContext context = new SessionContext(
            sessionId,
            Collections.emptyList(),
            null,
            Collections.emptyMap(),
            Collections.emptyList(),
            new ModelOptions(0.0, 2048, "gpt-4o"), // or your preferred model
            ToDoList.empty()
        );

        System.out.println("--- Ganglia CLI (Streaming Enabled) ---");
        System.out.println("Session ID: " + sessionId);
        
        String input = args.length > 0 ? String.join(" ", args) : "Hello, how can you help me today?";
        System.out.println("\nUser: " + input);
        System.out.print("Agent: ");

        // 3. Run with Streaming
        ui.listenToStream(sessionId);
        
        agentLoop.run(input, context)
            .onComplete(ar -> {
                if (ar.succeeded()) {
                    System.out.println("\n\n[Done]");
                } else {
                    System.err.println("\n\nError: " + ar.cause().getMessage());
                }
                vertx.close();
            });
    }
}