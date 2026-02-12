package me.stream.ganglia.it;

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
import me.stream.ganglia.core.prompt.PromptEngine;
import me.stream.ganglia.core.session.DefaultSessionManager;
import me.stream.ganglia.core.session.SessionManager;
import me.stream.ganglia.core.state.FileLogManager;
import me.stream.ganglia.core.state.StateEngine;
import me.stream.ganglia.tools.DefaultToolExecutor;
import me.stream.ganglia.tools.ToolsFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(VertxExtension.class)
public class AgentLoopIT {

    private ReActAgentLoop agentLoop;
    private SessionContext sessionContext;

    @BeforeEach
    void setUp(Vertx vertx) {
        String apiKey = System.getenv("MOONSHOT_API_KEY");
        String baseUrl = "https://api.moonshot.cn/v1";

        OpenAIModelGateway modelGateway = new OpenAIModelGateway(vertx, apiKey, baseUrl);
        ContextCompressor compressor = new ContextCompressor(modelGateway);
        KnowledgeBase knowledgeBase = new KnowledgeBase(vertx, "TEST_MEMORY_IT.md"); // Dummy

        ToolsFactory toolsFactory = new ToolsFactory(vertx, compressor, knowledgeBase);
        DefaultToolExecutor toolExecutor = new DefaultToolExecutor(toolsFactory, null);

                // Mocking simple components

                StateEngine stateEngine = mock(StateEngine.class);

                when(stateEngine.saveSession(any())).thenReturn(io.vertx.core.Future.succeededFuture());

                FileLogManager logManager = new FileLogManager(vertx);
                SessionManager sessionManager = new DefaultSessionManager(stateEngine, logManager);

                PromptEngine promptEngine = context -> io.vertx.core.Future.succeededFuture("You are a helpful assistant with bash file access tools. " +
                "Your task is to list files in 'src/test/resources/integration' using 'ls', read them using 'cat', and concatenate their content. " +
                "The final answer should only be the concatenated string without spaces or newlines.");



                agentLoop = new ReActAgentLoop(modelGateway, toolExecutor, sessionManager, promptEngine, 10);



        ModelOptions options = new ModelOptions(0.0, 1024, "moonshot-v1-8k");
        sessionContext = new SessionContext(UUID.randomUUID().toString(), Collections.emptyList(), null, Collections.emptyMap(), Collections.emptyList(), options, ToDoList.empty());
    }

    @Test
    void testFileConcatenation(VertxTestContext testContext) {
        String input = "Please list files in 'src/test/resources/integration' using 'ls', read all of them using 'cat', and concatenate their content into one string. Ignore all spaces and newlines in the final result.";

        agentLoop.run(input, sessionContext)
                .onComplete(testContext.succeeding(result -> {
                    testContext.verify(() -> {
                        assertTrue(result.contains("ab"), "Result should contain 'ab', but was: " + result);
                        testContext.completeNow();
                    });
                }));

        // This is the correct way to increase timeout for this test context in Vertx JUnit 5
        try {
            java.util.concurrent.TimeUnit.SECONDS.sleep(0); // Dummy to keep structure
        } catch (InterruptedException e) {}
    }
}
