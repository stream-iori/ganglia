package work.ganglia.infrastructure.external.llm;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import work.ganglia.port.chat.Message;
import work.ganglia.port.external.llm.ModelGateway;
import work.ganglia.port.external.llm.ModelOptions;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.internal.state.AgentSignal;
import work.ganglia.port.internal.state.TokenUsage;
import work.ganglia.stubs.StubExecutionContext;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(VertxExtension.class)
public class RetryingModelGatewayTest {

    private ModelOptions options = new ModelOptions(0.0, 100, "test", true);
    private List<Message> history = Collections.emptyList();
    private AgentSignal signal = new AgentSignal();

    @Test
    void testNoRetryOnSuccess(Vertx vertx, VertxTestContext testContext) {
        ModelGateway delegate = new ModelGateway() {
            @Override
            public Future<ModelResponse> chat(List<Message> history, List<work.ganglia.port.external.tool.ToolDefinition> availableTools, ModelOptions options, AgentSignal signal) {
                return Future.succeededFuture(new ModelResponse("Success", Collections.emptyList(), new TokenUsage(10, 10)));
            }
            @Override
            public Future<ModelResponse> chatStream(List<Message> history, List<work.ganglia.port.external.tool.ToolDefinition> availableTools, ModelOptions options, work.ganglia.port.internal.state.ExecutionContext context, AgentSignal signal) {
                return Future.succeededFuture(new ModelResponse("Success", Collections.emptyList(), new TokenUsage(10, 10)));
            }
        };

        RetryingModelGateway gateway = new RetryingModelGateway(delegate, vertx, 3);
        gateway.chatStream(history, Collections.emptyList(), options, new StubExecutionContext(), signal)
            .onComplete(testContext.succeeding(res -> testContext.verify(() -> {
                assertEquals("Success", res.content());
                testContext.completeNow();
            })));
    }

    @Test
    void testRetryOnConnectExceptionAndEmitWarning(Vertx vertx, VertxTestContext testContext) {
        AtomicInteger attempts = new AtomicInteger(0);
        ModelGateway delegate = new ModelGateway() {
            @Override
            public Future<ModelResponse> chat(List<Message> history, List<work.ganglia.port.external.tool.ToolDefinition> availableTools, ModelOptions options, AgentSignal signal) {
                return Future.failedFuture("Not used");
            }
            @Override
            public Future<ModelResponse> chatStream(List<Message> history, List<work.ganglia.port.external.tool.ToolDefinition> availableTools, ModelOptions options, work.ganglia.port.internal.state.ExecutionContext context, AgentSignal signal) {
                if (attempts.incrementAndGet() < 2) {
                    return Future.failedFuture(new ConnectException("Connection refused"));
                }
                return Future.succeededFuture(new ModelResponse("Recovered", Collections.emptyList(), new TokenUsage(10, 10)));
            }
        };

        RetryingModelGateway gateway = new RetryingModelGateway(delegate, vertx, 3);
        StubExecutionContext context = new StubExecutionContext();
        gateway.chatStream(history, Collections.emptyList(), options, context, signal)
            .onComplete(testContext.succeeding(res -> testContext.verify(() -> {
                assertEquals("Recovered", res.content());
                assertEquals(2, attempts.get());
                
                // Verify warning was emitted to stream
                boolean hasWarning = context.getStreams().stream().anyMatch(s -> s.contains("⚠️ Network error") && s.contains("Connection refused"));
                assertTrue(hasWarning, "Should have emitted network error warning");
                
                testContext.completeNow();
            })));
    }

    @Test
    void testFailAfterMaxRetries(Vertx vertx, VertxTestContext testContext) {
        AtomicInteger attempts = new AtomicInteger(0);
        ModelGateway delegate = new ModelGateway() {
            @Override
            public Future<ModelResponse> chat(List<Message> history, List<work.ganglia.port.external.tool.ToolDefinition> availableTools, ModelOptions options, AgentSignal signal) {
                return Future.failedFuture("Not used");
            }
            @Override
            public Future<ModelResponse> chatStream(List<Message> history, List<work.ganglia.port.external.tool.ToolDefinition> availableTools, ModelOptions options, work.ganglia.port.internal.state.ExecutionContext context, AgentSignal signal) {
                attempts.incrementAndGet();
                return Future.failedFuture(new LLMException("Server Error", null, 502, "Bad Gateway", null));
            }
        };

        int maxRetries = 2;
        RetryingModelGateway gateway = new RetryingModelGateway(delegate, vertx, maxRetries);
        gateway.chatStream(history, Collections.emptyList(), options, new StubExecutionContext(), signal)
            .onComplete(testContext.failing(err -> testContext.verify(() -> {
                // Initial attempt + maxRetries
                assertEquals(1 + maxRetries, attempts.get());
                assertTrue(err instanceof LLMException);
                testContext.completeNow();
            })));
    }
}
