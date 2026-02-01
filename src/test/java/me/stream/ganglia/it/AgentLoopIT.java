package me.stream.ganglia.it;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import me.stream.ganglia.core.llm.OpenAIModelGateway;
import me.stream.ganglia.core.loop.ReActAgentLoop;
import me.stream.ganglia.core.model.ModelOptions;
import me.stream.ganglia.core.model.SessionContext;
import me.stream.ganglia.core.prompt.PromptEngine;
import me.stream.ganglia.core.state.StateEngine;
import me.stream.ganglia.core.tools.DefaultToolExecutor;
import me.stream.ganglia.core.tools.ToolsFactory;
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
        
        ToolsFactory toolsFactory = new ToolsFactory(vertx);
        DefaultToolExecutor toolExecutor = new DefaultToolExecutor(toolsFactory);
        
        // Mocking simple components
        StateEngine stateEngine = mock(StateEngine.class);

        when(stateEngine.saveSession(any())).thenReturn(io.vertx.core.Future.succeededFuture());

        PromptEngine promptEngine = context -> "You are a helpful assistant with file access tools. " +
                "Your task is to list files in 'src/test/resources/integration', read them, and concatenate their content. " +
                "The final answer should only be the concatenated string without spaces or newlines.";

        agentLoop = new ReActAgentLoop(modelGateway, toolExecutor, stateEngine, promptEngine, 10);

        ModelOptions options = new ModelOptions(0.0, 1024 * 128, "kimi-k2-thinking");
        sessionContext = new SessionContext(UUID.randomUUID().toString(), Collections.emptyList(), Collections.emptyMap(), Collections.emptyList(), options);
    }

    @Test
    void testFileConcatenation(VertxTestContext testContext) {
        String input = "Please list files in 'src/test/resources/integration', read all of them, and concatenate their content into one string. Ignore all spaces and newlines in the final result.";

        agentLoop.run(input, sessionContext)
                .onComplete(testContext.succeeding(result -> {
                    testContext.verify(() -> {
                        // The result should be 'ab' (a.txt contains 'a', b.txt contains 'b')
                        // LLM might return 'ab' or 'The result is ab', so we check for containment or exact match
                        // depending on prompt strictness.
                        assertTrue(result.contains("ab"), "Result should contain 'ab', but was: " + result);
                        testContext.completeNow();
                    });
                }));
    }
}
