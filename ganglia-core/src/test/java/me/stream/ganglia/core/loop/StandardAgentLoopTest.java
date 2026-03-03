package me.stream.ganglia.core.loop;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import me.stream.ganglia.core.model.*;
import me.stream.ganglia.core.schedule.DefaultSchedulableFactory;
import me.stream.ganglia.core.schedule.SchedulableFactory;
import me.stream.ganglia.core.session.DefaultSessionManager;
import me.stream.ganglia.core.session.SessionManager;
import me.stream.ganglia.stubs.*;
import me.stream.ganglia.tools.model.ToDoList;
import me.stream.ganglia.tools.model.ToolCall;
import me.stream.ganglia.tools.model.ToolInvokeResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(VertxExtension.class)
class StandardAgentLoopTest {

    Vertx vertx;
    StubModelGateway model;
    StubToolExecutor tools;
    InMemoryStateEngine state;
    InMemoryLogManager logManager;
    StubPromptEngine prompt;
    StubConfigManager configManager;
    SessionManager sessionManager;
    me.stream.ganglia.memory.ContextCompressor compressor;
    SchedulableFactory scheduleableFactory;
    StandardAgentLoop loop;

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx();
        model = new StubModelGateway();
        tools = new StubToolExecutor();
        state = new InMemoryStateEngine();
        logManager = new InMemoryLogManager();
        prompt = new StubPromptEngine();
        configManager = new StubConfigManager(vertx);
        sessionManager = new DefaultSessionManager(state, logManager, configManager);
        compressor = new me.stream.ganglia.memory.ContextCompressor(model, configManager);
        scheduleableFactory = new DefaultSchedulableFactory(vertx, model, sessionManager, prompt, configManager, compressor, tools, null, null, null);
        loop = new StandardAgentLoop(vertx, model, scheduleableFactory, sessionManager, prompt, configManager, compressor);
    }

    @Test
    void testHappyPathMultipleTools(VertxTestContext testContext) {
        ModelOptions options = new ModelOptions(0.7, 1000, "gpt-4");
        SessionContext context = new SessionContext("test-session", Collections.emptyList(), null, Collections.emptyMap(), Collections.emptyList(), options, ToDoList.empty());

        // Setup Responses
        // 1st Model Call: Returns TWO Tool Calls
        ToolCall toolCall1 = new ToolCall("call-1", "test-tool-1", Map.of("arg", "1"));
        ToolCall toolCall2 = new ToolCall("call-2", "test-tool-2", Map.of("arg", "2"));
        ModelResponse toolResponse = new ModelResponse("Thinking...", List.of(toolCall1, toolCall2), new TokenUsage(10, 10));

        // 2nd Model Call: Returns Final Answer
        ModelResponse finalResponse = new ModelResponse("Final Answer", Collections.emptyList(), new TokenUsage(10, 10));

        model.addResponses(toolResponse, finalResponse);

        // Setup Tools
        tools.registerHandler("test-tool-1", call -> ToolInvokeResult.success("Result 1"));
        tools.registerHandler("test-tool-2", call -> ToolInvokeResult.success("Result 2"));

        loop.run("Hello", context).onComplete(testContext.succeeding(result -> {
            testContext.verify(() -> {
                assertEquals("Final Answer", result);
                assertEquals(2, tools.getExecutedCalls().size());
                assertNotNull(state.getSessions().get("test-session"));
                testContext.completeNow();
            });
        }));
    }

    @Test
    void testInterruptAndResume(VertxTestContext testContext) {
        ModelOptions options = new ModelOptions(0.7, 1000, "gpt-4");
        SessionContext context = new SessionContext("test-session-interrupt", Collections.emptyList(), null, Collections.emptyMap(), Collections.emptyList(), options, ToDoList.empty());

        // 1. Initial Run -> Interrupt
        ToolCall selectionCall = new ToolCall("call-select", "ask_selection", Map.of("q", "Choose"));
        ModelResponse toolResponse = new ModelResponse("Thinking...", List.of(selectionCall), new TokenUsage(10, 10));
        model.addResponse(toolResponse);

        tools.registerHandler("ask_selection", call -> ToolInvokeResult.interrupt("Please choose: A or B"));

        loop.run("Help me choose", context).onComplete(testContext.succeeding(promptMsg -> {
            testContext.verify(() -> {
                assertEquals("Please choose: A or B", promptMsg);

                // Get saved session state
                SessionContext pausedContext = state.getSessions().get("test-session-interrupt");
                assertNotNull(pausedContext);

                // 2. Resume -> Success
                ModelResponse finalResponse = new ModelResponse("You chose A", Collections.emptyList(), new TokenUsage(10, 10));
                model.addResponse(finalResponse);

                loop.resume("A", pausedContext).onComplete(testContext.succeeding(finalResult -> {
                    testContext.verify(() -> {
                        assertEquals("You chose A", finalResult);
                        testContext.completeNow();
                    });
                }));
            });
        }));
    }

    @Test
    void testStreamingFeedback(VertxTestContext testContext) {
        ModelOptions options = new ModelOptions(0.7, 1000, "gpt-4");
        String sessionId = "stream-session";
        SessionContext context = new SessionContext(sessionId, Collections.emptyList(), null, Collections.emptyMap(), Collections.emptyList(), options, ToDoList.empty());

        ModelResponse finalResponse = new ModelResponse("Final Answer", Collections.emptyList(), new TokenUsage(10, 10));
        model.addResponse(finalResponse);

        // Run
        loop.run("Hello", context).onComplete(testContext.succeeding(result -> {
            testContext.verify(() -> {
                assertEquals("Final Answer", result);
                testContext.completeNow();
            });
        }));
    }

    @Test
    void testIterationLimitReached(VertxTestContext testContext) {
        // 1. Setup Config with maxIterations = 2
        configManager.setConfig(new io.vertx.core.json.JsonObject()
                .put("agent", new io.vertx.core.json.JsonObject().put("maxIterations", 2)));

        ModelOptions options = new ModelOptions(0.7, 1000, "gpt-4");
        SessionContext context = new SessionContext("test-iter-limit", Collections.emptyList(), null, Collections.emptyMap(), Collections.emptyList(), options, ToDoList.empty());

        // 2. Setup LLM to keep returning a tool call (infinite loop)
        ToolCall toolCall = new ToolCall("call-inf", "test-tool", Map.of("arg", "1"));
        ModelResponse toolResponse = new ModelResponse("Still working...", List.of(toolCall), new TokenUsage(10, 10));

        // Add 3 responses (one more than limit) to see if it stops
        model.addResponses(toolResponse, toolResponse, toolResponse);

        tools.registerHandler("test-tool", call -> ToolInvokeResult.success("Progress"));

        // 3. Run - Should return success with limit message
        loop.run("Start", context).onComplete(testContext.succeeding(result -> {
            testContext.verify(() -> {
                assertEquals("Max iterations reached without final answer.", result);
                testContext.completeNow();
            });
        }));
    }
}
