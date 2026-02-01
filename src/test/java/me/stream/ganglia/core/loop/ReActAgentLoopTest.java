package me.stream.ganglia.core.loop;

import io.vertx.core.Future;
import me.stream.ganglia.core.llm.ModelGateway;
import me.stream.ganglia.core.model.*;
import me.stream.ganglia.core.prompt.PromptEngine;
import me.stream.ganglia.core.state.StateEngine;
import me.stream.ganglia.core.tools.ToolExecutor;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReActAgentLoopTest {

    @Test
    void testHappyPath() throws Exception {
        // Setup
        MockModelGateway model = new MockModelGateway();
        MockToolExecutor tools = new MockToolExecutor();
        MockStateEngine state = new MockStateEngine();
        MockPromptEngine prompt = new MockPromptEngine();
        
        ReActAgentLoop loop = new ReActAgentLoop(model, tools, state, prompt, 5);
        ModelOptions options = new ModelOptions(0.7, 1000, "gpt-4");
        SessionContext context = new SessionContext("test-session", Collections.emptyList(), Collections.emptyMap(), Collections.emptyList(), options);

        // Run (Wait for Future to complete)
        String result = loop.run("Hello", context).toCompletionStage().toCompletableFuture().get();

        // Verify
        assertEquals("Final Answer", result);
        assertTrue(model.callCount > 0);
        assertTrue(tools.executionCount > 0);
    }

    // Mocks

    static class MockModelGateway implements ModelGateway {
        int callCount = 0;

        @Override
        public Future<ModelResponse> chat(List<Message> history, List<ToolDefinition> availableTools, ModelOptions options) {
            callCount++;
            // Check if options are passed correctly
            if (!"gpt-4".equals(options.modelName())) {
                return Future.failedFuture("Wrong model options passed");
            }

            // 1st call: Return a tool call
            if (callCount == 1) {
                ToolCall call = new ToolCall("call-1", "test-tool", Map.of("arg", "val"));
                return Future.succeededFuture(new ModelResponse("Thinking...", List.of(call), new TokenUsage(10, 10)));
            }
            // 2nd call: Return final answer
            return Future.succeededFuture(new ModelResponse("Final Answer", Collections.emptyList(), new TokenUsage(10, 10)));
        }

        @Override
        public Future<Void> chatStream(List<Message> history, List<ToolDefinition> availableTools, ModelOptions options, String streamAddress) {
            return Future.succeededFuture(); // Not used in test
        }
    }

    static class MockToolExecutor implements ToolExecutor {
        int executionCount = 0;

        @Override
        public Future<String> execute(ToolCall toolCall) {
            executionCount++;
            return Future.succeededFuture("Tool Result");
        }

        @Override
        public List<ToolDefinition> getAvailableTools() {
            return Collections.emptyList();
        }
    }

    static class MockStateEngine implements StateEngine {
        @Override
        public Future<SessionContext> loadSession(String sessionId) {
            return Future.succeededFuture(null);
        }

        @Override
        public Future<Void> saveSession(SessionContext context) {
            return Future.succeededFuture();
        }

        @Override
        public SessionContext createSession() {
            return null;
        }
    }

    static class MockPromptEngine implements PromptEngine {
        @Override
        public String buildSystemPrompt(SessionContext context) {
            return "System Prompt";
        }
    }
}
