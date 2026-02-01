package me.stream.ganglia.core.loop;

import me.stream.ganglia.core.llm.ModelGateway;
import me.stream.ganglia.core.model.*;
import me.stream.ganglia.core.prompt.PromptEngine;
import me.stream.ganglia.core.state.StateEngine;
import me.stream.ganglia.core.tools.ToolExecutor;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

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
        SessionContext context = new SessionContext("test-session", Collections.emptyList(), Collections.emptyMap(), Collections.emptyList());

        // Run
        String result = loop.run("Hello", context).toCompletableFuture().get();

        // Verify
        assertEquals("Final Answer", result);
        assertTrue(model.callCount > 0);
        assertTrue(tools.executionCount > 0);
    }

    // Mocks

    static class MockModelGateway implements ModelGateway {
        int callCount = 0;

        @Override
        public CompletionStage<ModelResponse> chat(List<Message> history, List<ToolDefinition> availableTools, ModelOptions options) {
            callCount++;
            // 1st call: Return a tool call
            if (callCount == 1) {
                ToolCall call = new ToolCall("call-1", "test-tool", Map.of("arg", "val"));
                return CompletableFuture.completedFuture(new ModelResponse("Thinking...", List.of(call), new TokenUsage(10, 10)));
            }
            // 2nd call: Return final answer
            return CompletableFuture.completedFuture(new ModelResponse("Final Answer", Collections.emptyList(), new TokenUsage(10, 10)));
        }

        @Override
        public Flow.Publisher<String> chatStream(List<Message> history, List<ToolDefinition> availableTools, ModelOptions options) {
            return null; // Not used
        }
    }

    static class MockToolExecutor implements ToolExecutor {
        int executionCount = 0;

        @Override
        public CompletionStage<String> execute(ToolCall toolCall) {
            executionCount++;
            return CompletableFuture.completedFuture("Tool Result");
        }

        @Override
        public List<ToolDefinition> getAvailableTools() {
            return Collections.emptyList();
        }
    }

    static class MockStateEngine implements StateEngine {
        @Override
        public CompletionStage<SessionContext> loadSession(String sessionId) {
            return null; 
        }

        @Override
        public CompletionStage<Void> saveSession(SessionContext context) {
            return CompletableFuture.completedFuture(null);
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
