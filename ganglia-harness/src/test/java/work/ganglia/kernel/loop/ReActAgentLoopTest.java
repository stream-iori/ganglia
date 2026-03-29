package work.ganglia.kernel.loop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.vertx.junit5.VertxTestContext;

import work.ganglia.infrastructure.external.tool.model.ToolInvokeResult;
import work.ganglia.kernel.BaseKernelTest;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.internal.state.AgentSignal;

public class ReActAgentLoopTest extends BaseKernelTest {

  @Test
  void testSuccessfulDirectAnswer(VertxTestContext testContext) {
    String answer = "The capital of France is Paris.";
    model.addResponse(new ModelResponse(answer, Collections.emptyList(), null));

    SessionContext context = createSessionContext();

    loop.run("What is the capital of France?", context, new AgentSignal())
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertTrue(result.contains("Paris"));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testToolCallAndRecursion(VertxTestContext testContext) {
    // Turn 1: Model calls a tool
    ToolCall call = new ToolCall("c1", "test_tool", Map.of("arg", "val"));
    model.addResponse(new ModelResponse("I need to use a tool.", List.of(call), null));

    // Turn 2: Model gives final answer after tool result
    model.addResponse(new ModelResponse("The tool said OK.", Collections.emptyList(), null));

    SessionContext context = createSessionContext();

    loop.run("Use the tool.", context, new AgentSignal())
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertTrue(result.contains("said OK"));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testAgentInterruptAndResume(VertxTestContext testContext) {
    // Turn 1: Model calls a tool that requires user input
    ToolCall call = new ToolCall("c1", "ask_user", Map.of("question", "Do you approve?"));
    model.addResponse(new ModelResponse("I need your approval.", List.of(call), null));

    // Turn 2: Model gives final answer after tool result
    model.addResponse(new ModelResponse("Approved. Proceeding.", Collections.emptyList(), null));

    tools.registerHandler(
        "ask_user",
        tc ->
            work.ganglia.infrastructure.external.tool.model.ToolInvokeResult.interrupt(
                "Pause for input"));

    SessionContext context = createSessionContext();

    loop.run("Do task", context, new AgentSignal())
        .onComplete(
            testContext.succeeding(
                result1 -> {
                  testContext.verify(() -> assertTrue(result1.contains("Pause for input")));

                  // Fetch the updated context from memory to simulate a fresh resume request
                  sessionManager
                      .getSession(context.sessionId())
                      .onComplete(
                          testContext.succeeding(
                              updatedContext -> {
                                loop.resume(
                                        null, "Yes, I approve", updatedContext, new AgentSignal())
                                    .onComplete(
                                        testContext.succeeding(
                                            result2 -> {
                                              testContext.verify(
                                                  () -> {
                                                    assertTrue(
                                                        result2.contains("Approved. Proceeding."));
                                                    testContext.completeNow();
                                                  });
                                            }));
                              }));
                }));
  }

  @Test
  void testConsecutiveFailuresAbortLoop(VertxTestContext testContext) {
    // Register a tool that always returns ERROR
    tools.registerHandler("failing_tool", tc -> ToolInvokeResult.error("tool failed"));

    // 3 iterations, each calling the failing tool — default threshold is 3
    ToolCall call1 = new ToolCall("c1", "failing_tool", Map.of());
    model.addResponse(new ModelResponse("Trying tool.", List.of(call1), null));

    ToolCall call2 = new ToolCall("c2", "failing_tool", Map.of());
    model.addResponse(new ModelResponse("Trying again.", List.of(call2), null));

    ToolCall call3 = new ToolCall("c3", "failing_tool", Map.of());
    model.addResponse(new ModelResponse("One more try.", List.of(call3), null));

    SessionContext context = createSessionContext();

    loop.run("Do something", context, new AgentSignal())
        .onComplete(
            testContext.failing(
                err -> {
                  testContext.verify(
                      () -> {
                        assertTrue(err.getMessage().contains("repetitive task failures"));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testSuccessResetsFailureCounter(VertxTestContext testContext) {
    // Fail twice, then succeed, then fail twice more — should NOT abort (counter resets)
    tools.registerHandler("failing_tool", tc -> ToolInvokeResult.error("tool failed"));

    // Iteration 1: fail
    ToolCall fail1 = new ToolCall("c1", "failing_tool", Map.of());
    model.addResponse(new ModelResponse("Try 1.", List.of(fail1), null));

    // Iteration 2: fail
    ToolCall fail2 = new ToolCall("c2", "failing_tool", Map.of());
    model.addResponse(new ModelResponse("Try 2.", List.of(fail2), null));

    // Iteration 3: succeed (resets counter)
    ToolCall ok = new ToolCall("c3", "test_tool", Map.of("arg", "val"));
    model.addResponse(new ModelResponse("Now succeed.", List.of(ok), null));

    // Iteration 4: final answer
    model.addResponse(new ModelResponse("All done.", Collections.emptyList(), null));

    SessionContext context = createSessionContext();

    loop.run("Do something", context, new AgentSignal())
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertTrue(result.contains("All done"));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testSingleFailureDoesNotAbort(VertxTestContext testContext) {
    // One failure followed by a final answer — should succeed
    tools.registerHandler("failing_tool", tc -> ToolInvokeResult.error("tool failed"));

    ToolCall fail1 = new ToolCall("c1", "failing_tool", Map.of());
    model.addResponse(new ModelResponse("Try tool.", List.of(fail1), null));

    model.addResponse(new ModelResponse("Recovered gracefully.", Collections.emptyList(), null));

    SessionContext context = createSessionContext();

    loop.run("Do something", context, new AgentSignal())
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertTrue(result.contains("Recovered gracefully"));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testAgentStop(VertxTestContext testContext) {
    SessionContext context = createSessionContext();
    AgentSignal signal = new AgentSignal();

    // Abort the signal before running
    signal.abort();

    loop.run("Do task", context, signal)
        .onComplete(
            testContext.failing(
                err -> {
                  testContext.verify(
                      () -> {
                        assertTrue(err instanceof AgentAbortedException);
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testSessionCleanupAfterNormalCompletion(VertxTestContext testContext) {
    String answer = "Done.";
    model.addResponse(new ModelResponse(answer, Collections.emptyList(), null));

    SessionContext context = createSessionContext();

    loop.run("Hello", context, new AgentSignal())
        .onComplete(
            testContext.succeeding(
                result ->
                    testContext.verify(
                        () -> {
                          assertEquals(
                              0,
                              loop.getActiveSessionCount(),
                              "Session maps should be empty after normal completion");
                          testContext.completeNow();
                        })));
  }

  @Test
  void testSessionCleanupAfterAbort(VertxTestContext testContext) {
    SessionContext context = createSessionContext();
    AgentSignal signal = new AgentSignal();
    signal.abort();

    loop.run("Hello", context, signal)
        .onComplete(
            testContext.failing(
                err ->
                    testContext.verify(
                        () -> {
                          assertEquals(
                              0,
                              loop.getActiveSessionCount(),
                              "Session maps should be empty after abort");
                          testContext.completeNow();
                        })));
  }
}
