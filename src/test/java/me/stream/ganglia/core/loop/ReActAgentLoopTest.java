package me.stream.ganglia.core.loop;

import io.vertx.core.Future;
import me.stream.ganglia.core.llm.ModelGateway;
import me.stream.ganglia.core.model.*;
import me.stream.ganglia.core.tools.model.*;
import me.stream.ganglia.core.prompt.PromptEngine;
import me.stream.ganglia.core.state.LogManager;
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
    LogManager logManager;
    @Mock
    PromptEngine prompt;

    @Test
    void testHappyPathMultipleTools() throws Exception {
        // Setup
        ReActAgentLoop loop = new ReActAgentLoop(model, tools, state, logManager, prompt, 5);
        ModelOptions options = new ModelOptions(0.7, 1000, "gpt-4");
        SessionContext context = new SessionContext("test-session", Collections.emptyList(), null, Collections.emptyMap(), Collections.emptyList(), options, ToDoList.empty());

        // Mocks behavior
        when(state.saveSession(any())).thenReturn(Future.succeededFuture());
        when(logManager.appendLog(any())).thenReturn(Future.succeededFuture());
        when(prompt.buildSystemPrompt(any())).thenReturn(Future.succeededFuture("System Prompt"));
        when(tools.getAvailableTools(any())).thenReturn(Collections.emptyList());

        // 1st Model Call: Returns TWO Tool Calls
        ToolCall toolCall1 = new ToolCall("call-1", "test-tool-1", Map.of("arg", "1"));
        ToolCall toolCall2 = new ToolCall("call-2", "test-tool-2", Map.of("arg", "2"));

        ModelResponse toolResponse = new ModelResponse("Thinking...", List.of(toolCall1, toolCall2), new TokenUsage(10, 10));

        // 2nd Model Call: Returns Final Answer
        ModelResponse finalResponse = new ModelResponse("Final Answer", Collections.emptyList(), new TokenUsage(10, 10));

        when(model.chat(anyList(), anyList(), eq(options)))
                .thenReturn(Future.succeededFuture(toolResponse)) // 1st call
                .thenReturn(Future.succeededFuture(finalResponse)); // 2nd call

        // Tool Execution
        when(tools.execute(eq(toolCall1), any())).thenReturn(Future.succeededFuture(ToolInvokeResult.success("Result 1")));
        when(tools.execute(eq(toolCall2), any())).thenReturn(Future.succeededFuture(ToolInvokeResult.success("Result 2")));

        // Run
        String result = loop.run("Hello", context).toCompletionStage().toCompletableFuture().get();

        // Verify
        assertEquals("Final Answer", result);

        // Verify interactions
        verify(model, times(2)).chat(anyList(), anyList(), eq(options));
        verify(tools, times(1)).execute(eq(toolCall1), any());
        verify(tools, times(1)).execute(eq(toolCall2), any());
        verify(state, atLeast(1)).saveSession(any());
    }

    @Test
    void testInterruptAndResume() throws Exception {
        // Setup
        ReActAgentLoop loop = new ReActAgentLoop(model, tools, state, logManager, prompt, 5);
        ModelOptions options = new ModelOptions(0.7, 1000, "gpt-4");
        SessionContext context = new SessionContext("test-session", Collections.emptyList(), null, Collections.emptyMap(), Collections.emptyList(), options, ToDoList.empty());

        when(state.saveSession(any())).thenReturn(Future.succeededFuture());
        when(logManager.appendLog(any())).thenReturn(Future.succeededFuture());
        when(prompt.buildSystemPrompt(any())).thenReturn(Future.succeededFuture("System Prompt"));
        when(tools.getAvailableTools(any())).thenReturn(Collections.emptyList());

        // 1. Initial Run -> Interrupt
        ToolCall selectionCall = new ToolCall("call-select", "ask_selection", Map.of("q", "Choose"));
        ModelResponse toolResponse = new ModelResponse("Thinking...", List.of(selectionCall), new TokenUsage(10, 10));
        when(model.chat(anyList(), anyList(), eq(options))).thenReturn(Future.succeededFuture(toolResponse));

        when(tools.execute(eq(selectionCall), any()))
                .thenReturn(Future.succeededFuture(ToolInvokeResult.interrupt("Please choose: A or B")));

        // Execute run
        try {
            loop.run("Help me choose", context).toCompletionStage().toCompletableFuture().get();
        } catch (java.util.concurrent.ExecutionException e) {
            // Should fail with the prompt? No, my logic CATCHES the exception and returns the prompt as success.
            // Wait, I implemented `recover` in `runLoop` to return `succeededFuture(prompt)`.
            // So it should NOT throw exception here.
        }
        
        // Re-run correctly to capture result
        String promptMsg = loop.run("Help me choose", context).toCompletionStage().toCompletableFuture().get();
        assertEquals("Please choose: A or B", promptMsg);

        // Verify state: Context should have the Assistant message (Thinking...)
        // But since I mocked `state.saveSession`, the context object in test isn't automatically updated unless I capture it.
        // But `resume` relies on context having the tool call.
        // `ReActAgentLoop` creates new context instances.
        // In a real app, `stateEngine` would persist it and we'd reload it.
        // Here, I need to manually construct the context state for `resume` OR verify that `state.saveSession` was called with the right context.
        
        // Let's capture the context passed to saveSession
        org.mockito.ArgumentCaptor<SessionContext> captor = org.mockito.ArgumentCaptor.forClass(SessionContext.class);
        verify(state, atLeast(1)).saveSession(captor.capture());
        SessionContext pausedContext = captor.getValue();
        
        // 2. Resume -> Success
        // Mock next model response
        ModelResponse finalResponse = new ModelResponse("You chose A", Collections.emptyList(), new TokenUsage(10, 10));
        when(model.chat(anyList(), anyList(), eq(options))).thenReturn(Future.succeededFuture(finalResponse));

        String finalResult = loop.resume("A", pausedContext).toCompletionStage().toCompletableFuture().get();
        assertEquals("You chose A", finalResult);
    }
}
