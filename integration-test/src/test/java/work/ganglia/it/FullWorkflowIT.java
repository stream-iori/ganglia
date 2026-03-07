package work.ganglia.it;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.http.HttpServer;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import work.Main; 
import work.ganglia.Ganglia;
import work.ganglia.port.external.llm.ModelGateway;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.internal.state.TokenUsage;
import work.ganglia.port.external.tool.ToolCall;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
public class FullWorkflowIT {

    private Ganglia ganglia;

    @Mock
    private ModelGateway mockModel;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext) {
        mockModel = mock(ModelGateway.class);
        when(mockModel.chat(any(), any(), any(), any())).thenReturn(Future.failedFuture("Reflection disabled in tests"));
        Main.bootstrap(vertx, ".ganglia/config.json", new JsonObject().put("webui", new JsonObject().put("enabled", false)), mockModel)
            .onComplete(testContext.succeeding((Ganglia g) -> {
                this.ganglia = g;
                testContext.completeNow();
            }));
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
                ToolCall fetchCall = new ToolCall("c1", "web_fetch", Map.of("url", url));
                ModelResponse res1 = new ModelResponse("I will fetch the command.", List.of(fetchCall), new TokenUsage(1, 1));

                ToolCall shellCall = new ToolCall("c2", "run_shell_command", Map.of("command", "echo SUCCESS"));
                ModelResponse res2 = new ModelResponse("I found the command. Executing...", List.of(shellCall), new TokenUsage(1, 1));

                ToolCall rememberCall = new ToolCall("c3", "remember", Map.of("fact", "Execution result was SUCCESS"));
                ModelResponse res3 = new ModelResponse("I will remember the result.", List.of(rememberCall), new TokenUsage(1, 1));

                ModelResponse res4 = new ModelResponse("All done.", Collections.emptyList(), new TokenUsage(1, 1));

                when(mockModel.chatStream(anyList(), anyList(), any(), anyString(), any()))
                    .thenReturn(Future.succeededFuture(res1))
                    .thenReturn(Future.succeededFuture(res2))
                    .thenReturn(Future.succeededFuture(res3))
                    .thenReturn(Future.succeededFuture(res4));

                // 3. Run Agent Loop
                SessionContext context = ganglia.sessionManager().createSession(UUID.randomUUID().toString());

                ganglia.agentLoop().run("Please fetch the secret command from my server and run it, then remember the result.", context)
                    .onComplete(testContext.succeeding(answer -> {
                        testContext.verify(() -> {
                            assertTrue(answer.contains("All done."));
                            s.close();
                            testContext.completeNow();
                        });
                    }));
            }));
    }
}
