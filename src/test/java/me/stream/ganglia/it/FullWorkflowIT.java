package me.stream.ganglia.it;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import me.stream.ganglia.core.llm.ModelGateway;
import me.stream.ganglia.core.loop.ReActAgentLoop;
import me.stream.ganglia.memory.ContextCompressor;
import me.stream.ganglia.memory.KnowledgeBase;
import me.stream.ganglia.core.model.*;
import me.stream.ganglia.core.prompt.StandardPromptEngine;
import me.stream.ganglia.core.session.DefaultSessionManager;
import me.stream.ganglia.core.session.SessionManager;
import me.stream.ganglia.core.state.StateEngine;
import me.stream.ganglia.tools.DefaultToolExecutor;
import me.stream.ganglia.tools.ToolsFactory;
import me.stream.ganglia.tools.model.ToolCall;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
public class FullWorkflowIT {

    private ReActAgentLoop agentLoop;
    private KnowledgeBase knowledgeBase;
    private static final String MEMORY_FILE = "FULL_WORKFLOW_MEMORY.md";

    @Mock
    private ModelGateway modelGateway;
    @Mock
    private StateEngine stateEngine;

    @BeforeEach
    void setUp(Vertx vertx) {
        knowledgeBase = new KnowledgeBase(vertx, MEMORY_FILE);
        ContextCompressor compressor = new ContextCompressor(modelGateway);
        ToolsFactory toolsFactory = new ToolsFactory(vertx, compressor, knowledgeBase);
        DefaultToolExecutor toolExecutor = new DefaultToolExecutor(toolsFactory, null);

        when(stateEngine.saveSession(any())).thenReturn(io.vertx.core.Future.succeededFuture());
        SessionManager sessionManager = new DefaultSessionManager(stateEngine, null);

        StandardPromptEngine promptEngine = new StandardPromptEngine(vertx, knowledgeBase, null, null);
        agentLoop = new ReActAgentLoop(modelGateway, toolExecutor, sessionManager, promptEngine, 10);
    }

    @Test
    void testWebToShellToMemory(Vertx vertx, VertxTestContext testContext) {
        // 1. Start Mock Web Server
        HttpServer server = vertx.createHttpServer();
        server.requestHandler(req -> req.response().end("The secret command is 'echo SUCCESS'"))
            .listen(0)
            .onComplete(testContext.succeeding(s -> {
                int port = s.actualPort();
                String url = "http://localhost:" + port;

                // 2. Mock Model Interactions
                // Call 1: Decide to fetch URL
                ToolCall fetchCall = new ToolCall("c1", "web_fetch", Map.of("url", url));
                ModelResponse res1 = new ModelResponse("I will fetch the command.", List.of(fetchCall), new TokenUsage(1, 1));

                // Call 2: After seeing "The secret command is 'echo SUCCESS'", execute it
                ToolCall shellCall = new ToolCall("c2", "run_shell_command", Map.of("command", "echo SUCCESS"));
                ModelResponse res2 = new ModelResponse("I found the command. Executing...", List.of(shellCall), new TokenUsage(1, 1));

                // Call 3: After shell result, remember it
                ToolCall rememberCall = new ToolCall("c3", "remember", Map.of("fact", "Execution result was SUCCESS"));
                ModelResponse res3 = new ModelResponse("I will remember the result.", List.of(rememberCall), new TokenUsage(1, 1));

                // Call 4: Final answer
                ModelResponse res4 = new ModelResponse("All done.", Collections.emptyList(), new TokenUsage(1, 1));

                when(modelGateway.chatStream(anyList(), anyList(), any(), anyString()))
                    .thenReturn(io.vertx.core.Future.succeededFuture(res1))
                    .thenReturn(io.vertx.core.Future.succeededFuture(res2))
                    .thenReturn(io.vertx.core.Future.succeededFuture(res3))
                    .thenReturn(io.vertx.core.Future.succeededFuture(res4));

                // 3. Run Agent Loop
                SessionContext context = new SessionContext(UUID.randomUUID().toString(), Collections.emptyList(), null, Collections.emptyMap(), Collections.emptyList(), null, ToDoList.empty());

                agentLoop.run("Please fetch the secret command from my server and run it, then remember the result.", context)
                    .onComplete(testContext.succeeding(answer -> {
                        // 4. Verify Side Effects
                        knowledgeBase.read().onComplete(testContext.succeeding(memory -> {
                            testContext.verify(() -> {
                                assertTrue(memory.contains("Execution result was SUCCESS"));
                                s.close();
                                testContext.completeNow();
                            });
                        }));
                    }));
            }));
    }
}
