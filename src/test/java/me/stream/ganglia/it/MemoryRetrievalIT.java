package me.stream.ganglia.it;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import me.stream.ganglia.core.llm.OpenAIModelGateway;
import me.stream.ganglia.core.loop.ReActAgentLoop;
import me.stream.ganglia.core.memory.ContextCompressor;
import me.stream.ganglia.core.memory.KnowledgeBase;
import me.stream.ganglia.core.model.ModelOptions;
import me.stream.ganglia.core.model.SessionContext;
import me.stream.ganglia.core.model.ToDoList;
import me.stream.ganglia.core.prompt.PromptEngine;
import me.stream.ganglia.core.state.FileLogManager;
import me.stream.ganglia.core.state.StateEngine;
import me.stream.ganglia.core.tools.DefaultToolExecutor;
import me.stream.ganglia.core.tools.ToolsFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(VertxExtension.class)
public class MemoryRetrievalIT {

    private ReActAgentLoop agentLoop;
    private SessionContext sessionContext;
    private Vertx vertx;
    private static final String MEMORY_FILE = "src/test/resources/integration/MEMORY.md";

    @BeforeEach
    void setUp(Vertx vertx) {
        this.vertx = vertx;
        String apiKey = System.getenv("MOONSHOT_API_KEY");
        String baseUrl = "https://api.moonshot.cn/v1";

        OpenAIModelGateway modelGateway = new OpenAIModelGateway(vertx, apiKey, baseUrl);
        ContextCompressor compressor = new ContextCompressor(modelGateway);
        ToolsFactory toolsFactory = new ToolsFactory(vertx, compressor);
        DefaultToolExecutor toolExecutor = new DefaultToolExecutor(toolsFactory);
        
        StateEngine stateEngine = mock(StateEngine.class);
        when(stateEngine.saveSession(any())).thenReturn(io.vertx.core.Future.succeededFuture());
        FileLogManager logManager = new FileLogManager(vertx);
        
        PromptEngine promptEngine = context -> "You are a helpful assistant. " +
                "You have access to a knowledge base file at '" + MEMORY_FILE + "'. " +
                "If you don't know the answer, use your tools (ls, read, grep) to check that file. " +
                "Do not hallucinate.";

        agentLoop = new ReActAgentLoop(modelGateway, toolExecutor, stateEngine, logManager, promptEngine, 10);
        
        ModelOptions options = new ModelOptions(0.0, 1024, "moonshot-v1-8k");
        sessionContext = new SessionContext(UUID.randomUUID().toString(), Collections.emptyList(), null, Collections.emptyMap(), Collections.emptyList(), options, ToDoList.empty());
    }

    @AfterEach
    void tearDown(Vertx vertx, VertxTestContext testContext) {
        vertx.fileSystem().delete(MEMORY_FILE)
                .onComplete(ar -> testContext.completeNow());
    }

    @Test
    void testAgentRetrievesFromMemory(VertxTestContext testContext) {
        String secret = "The secret code is 998877";
        
        // 1. Write secret to MEMORY.md
        vertx.fileSystem().writeFile(MEMORY_FILE, Buffer.buffer(secret))
                .compose(v -> {
                    // 2. Ask Agent
                    String input = "What is the secret code in the memory file?";
                    return agentLoop.run(input, sessionContext);
                })
                .onComplete(testContext.succeeding(result -> {
                    testContext.verify(() -> {
                        assertTrue(result.contains("998877"), "Agent should retrieve the secret code. Result: " + result);
                        testContext.completeNow();
                    });
                }));
    }
}
