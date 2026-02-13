package me.stream.ganglia.it;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import me.stream.ganglia.core.llm.OpenAIModelGateway;
import me.stream.ganglia.core.loop.ReActAgentLoop;
import me.stream.ganglia.memory.ContextCompressor;
import me.stream.ganglia.memory.KnowledgeBase;
import me.stream.ganglia.core.model.ModelOptions;
import me.stream.ganglia.core.model.SessionContext;
import me.stream.ganglia.tools.model.ToDoList;
import me.stream.ganglia.core.prompt.StandardPromptEngine;
import me.stream.ganglia.core.session.DefaultSessionManager;
import me.stream.ganglia.core.session.SessionManager;
import me.stream.ganglia.core.state.FileLogManager;
import me.stream.ganglia.core.state.StateEngine;
import me.stream.ganglia.skills.SkillPromptInjector;
import me.stream.ganglia.skills.SkillRegistry;
import me.stream.ganglia.skills.SkillSuggester;
import me.stream.ganglia.tools.DefaultToolExecutor;
import me.stream.ganglia.tools.ToolsFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(VertxExtension.class)
public class SkillSystemIT {

    private ReActAgentLoop agentLoop;
    private SessionContext sessionContext;
    private SkillRegistry skillRegistry;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext) {
        String apiKey = System.getenv("MOONSHOT_API_KEY");
        String baseUrl = "https://api.moonshot.cn/v1";

        OpenAIModelGateway modelGateway = new OpenAIModelGateway(vertx, apiKey, baseUrl);
        me.stream.ganglia.core.config.ConfigManager configManager = mock(me.stream.ganglia.core.config.ConfigManager.class);
        when(configManager.getBaseUrl()).thenReturn(baseUrl);
        
        ContextCompressor compressor = new ContextCompressor(modelGateway, configManager);
        KnowledgeBase knowledgeBase = new KnowledgeBase(vertx, "target/TEST_MEMORY_SKILL_IT.md");

        skillRegistry = new SkillRegistry(vertx, List.of(Paths.get("src/test/resources/skills")));
        
        skillRegistry.init().onComplete(ar -> {
            if (ar.failed()) {
                testContext.failNow(ar.cause());
                return;
            }

            ToolsFactory toolsFactory = new ToolsFactory(vertx, compressor, knowledgeBase);
            DefaultToolExecutor toolExecutor = new DefaultToolExecutor(toolsFactory, skillRegistry);

            SkillPromptInjector skillInjector = new SkillPromptInjector(vertx, skillRegistry);
            SkillSuggester skillSuggester = new SkillSuggester(vertx, skillRegistry);
            StandardPromptEngine promptEngine = new StandardPromptEngine(vertx, knowledgeBase, skillInjector, skillSuggester);

            StateEngine stateEngine = mock(StateEngine.class);
            when(stateEngine.saveSession(any())).thenReturn(Future.succeededFuture());

            FileLogManager logManager = new FileLogManager(vertx);
            SessionManager sessionManager = new DefaultSessionManager(stateEngine, logManager, configManager);

            agentLoop = new ReActAgentLoop(modelGateway, toolExecutor, sessionManager, promptEngine, 5);

            ModelOptions options = new ModelOptions(0.0, 1024, "moonshot-v1-8k");
            sessionContext = new SessionContext(UUID.randomUUID().toString(), Collections.emptyList(), null, Collections.emptyMap(), Collections.emptyList(), options, ToDoList.empty());
            
            testContext.completeNow();
        });
    }

    @Test
    void testSkillDiscoveryAndActivation(Vertx vertx, VertxTestContext testContext) throws InterruptedException {
        // Create a file that triggers the skill suggestion
        vertx.fileSystem().writeFile("it-test.it-test", io.vertx.core.buffer.Buffer.buffer("trigger"))
            .compose(v -> {
                String input = "I see a .it-test file. What skills do you suggest and please activate the relevant one for me.";
                return agentLoop.run(input, sessionContext);
            })
            .onComplete(testContext.succeeding(result -> {
                testContext.verify(() -> {
                    // The model should have seen the suggestion and hopefully activated the skill.
                    // If it activated the skill and followed instructions, the response might contain SKILL_ACTIVE.
                    // Note: Activation requires user confirmation in the real tool, 
                    // but since this is an IT, we'll see if the agent at least tries to call the tool.
                    // Wait, ReActAgentLoop will interrupt when activate_skill is called.
                    
                    assertTrue(result.contains("IT Test Skill") || result.contains("activate_skill") || result.contains("SKILL_ACTIVE"), 
                        "Result should mention the skill or activation, but was: " + result);
                    
                    // Clean up
                    vertx.fileSystem().delete("it-test.it-test").onComplete(ar -> testContext.completeNow());
                });
            }))
            .onFailure(testContext::failNow);
    }
}
