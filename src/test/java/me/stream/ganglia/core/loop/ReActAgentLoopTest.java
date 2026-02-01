package me.stream.ganglia.core.loop;

import io.vertx.core.Future;
import me.stream.ganglia.core.llm.ModelGateway;
import me.stream.ganglia.core.model.*;
import me.stream.ganglia.core.prompt.PromptEngine;
import me.stream.ganglia.core.state.StateEngine;
import me.stream.ganglia.core.tools.ToolExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReActAgentLoopTest {

    @Mock
    ModelGateway model;
    @Mock
    ToolExecutor tools;
    @Mock
    StateEngine state;
    @Mock
    PromptEngine prompt;

    @Test
    void testHappyPath() throws Exception {
        // Setup
        ReActAgentLoop loop = new ReActAgentLoop(model, tools, state, prompt, 5);
        ModelOptions options = new ModelOptions(0.7, 1000, "gpt-4");
        SessionContext context = new SessionContext("test-session", Collections.emptyList(), Collections.emptyMap(), Collections.emptyList(), options);

        // Mocks behavior
        when(state.saveSession(any())).thenReturn(Future.succeededFuture());
        when(prompt.buildSystemPrompt(any())).thenReturn("System Prompt");
        when(tools.getAvailableTools()).thenReturn(Collections.emptyList());

        // 1st Model Call: Returns a Tool Call
        ToolCall toolCall = new ToolCall("call-1", "test-tool", Map.of("arg", "val"));
        ModelResponse toolResponse = new ModelResponse("Thinking...", List.of(toolCall), new TokenUsage(10, 10));
        
        // 2nd Model Call: Returns Final Answer
        ModelResponse finalResponse = new ModelResponse("Final Answer", Collections.emptyList(), new TokenUsage(10, 10));

        // We need to mock the sequence of model calls.
        // Note: The history passed to the model changes on each call. 
        // For simplicity in this test, we can use `anyList()` or capture arguments if we want to be strict.
        when(model.chat(anyList(), anyList(), eq(options)))
                .thenReturn(Future.succeededFuture(toolResponse)) // 1st call
                .thenReturn(Future.succeededFuture(finalResponse)); // 2nd call

        // Tool Execution
        when(tools.execute(eq(toolCall))).thenReturn(Future.succeededFuture("Tool Result"));

        // Run (Wait for Future to complete)
        String result = loop.run("Hello", context).toCompletionStage().toCompletableFuture().get();

        // Verify
        assertEquals("Final Answer", result);
        
        // Verify interactions
        verify(model, times(2)).chat(anyList(), anyList(), eq(options));
        verify(tools, times(1)).execute(eq(toolCall));
        verify(state, atLeast(1)).saveSession(any());
    }
}