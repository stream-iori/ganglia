package work.ganglia.kernel.loop;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.kernel.BaseKernelTest;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.external.tool.ObservationType;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.internal.state.AgentSignal;

@ExtendWith(VertxExtension.class)
public class ReActAgentLoopObservationTest extends BaseKernelTest {

  @Test
  void testObservationsPublishedDuringResume(VertxTestContext testContext) {
    // 1. Prepare Turn 1 (Interrupted)
    ToolCall call = new ToolCall("c1", "ask_user", Map.of("question", "Approved?"));
    model.addResponse(new ModelResponse("Need approval.", List.of(call), null));
    tools.registerHandler(
        "ask_user",
        tc -> work.ganglia.infrastructure.external.tool.model.ToolInvokeResult.interrupt("Pause"));

    // 2. Prepare Turn 2 (Resume response)
    model.addResponse(new ModelResponse("Confirmed.", Collections.emptyList(), null));

    SessionContext context = createSessionContext();
    List<ObservationType> observedTypes = Collections.synchronizedList(new ArrayList<>());

    // Register an observer to capture events
    if (loop.getDispatcher() instanceof DefaultObservationDispatcher dod) {
      dod.register(
          new AgentLoopObserver() {
            @Override
            public void onObservation(
                String sessionId, ObservationType type, String content, Map<String, Object> data) {
              observedTypes.add(type);
            }

            @Override
            public void onUsageRecorded(
                String sessionId, work.ganglia.port.internal.state.TokenUsage usage) {}
          });
    }

    loop.run("Start", context, new AgentSignal())
        .onComplete(
            testContext.succeeding(
                res1 -> {
                  sessionManager
                      .getSession(context.sessionId())
                      .onComplete(
                          testContext.succeeding(
                              updatedContext -> {
                                observedTypes.clear(); // Clear events from Turn 1

                                loop.resume(null, "Yes", updatedContext, new AgentSignal())
                                    .onComplete(
                                        testContext.succeeding(
                                            res2 -> {
                                              testContext.verify(
                                                  () -> {
                                                    assertTrue(
                                                        observedTypes.contains(
                                                            ObservationType.REASONING_STARTED),
                                                        "Should observe REASONING_STARTED during resume");
                                                    assertTrue(
                                                        observedTypes.contains(
                                                            ObservationType.REQUEST_PREPARED),
                                                        "Should observe REQUEST_PREPARED during resume");
                                                    assertTrue(
                                                        observedTypes.contains(
                                                            ObservationType.TURN_FINISHED),
                                                        "Should observe TURN_FINISHED during resume");
                                                    testContext.completeNow();
                                                  });
                                            }));
                              }));
                }));
  }

  @Test
  void testToolStartedObservation(VertxTestContext testContext) {
    ToolCall call = new ToolCall("c1", "bash", Map.of("command", "ls"));
    model.addResponse(new ModelResponse("Executing...", List.of(call), null));
    model.addResponse(new ModelResponse("Done.", Collections.emptyList(), null));
    tools.registerHandler(
        "bash",
        tc ->
            work.ganglia.infrastructure.external.tool.model.ToolInvokeResult.success("file1.txt"));

    SessionContext context = createSessionContext();
    List<ObservationType> observedTypes = Collections.synchronizedList(new ArrayList<>());

    if (loop.getDispatcher() instanceof DefaultObservationDispatcher dod) {
      dod.register(
          new AgentLoopObserver() {
            @Override
            public void onObservation(
                String sessionId, ObservationType type, String content, Map<String, Object> data) {
              observedTypes.add(type);
            }

            @Override
            public void onUsageRecorded(
                String sessionId, work.ganglia.port.internal.state.TokenUsage usage) {}
          });
    }

    loop.run("Run bash", context, new AgentSignal())
        .onComplete(
            testContext.succeeding(
                res -> {
                  testContext.verify(
                      () -> {
                        assertTrue(
                            observedTypes.contains(ObservationType.TOOL_STARTED),
                            "Should observe TOOL_STARTED");
                        assertTrue(
                            observedTypes.contains(ObservationType.TOOL_FINISHED),
                            "Should observe TOOL_FINISHED");
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testAbortedExceptionDoesNotPublishError(VertxTestContext testContext) {
    SessionContext context = createSessionContext();
    AgentSignal signal = new AgentSignal();

    AtomicBoolean errorObserved = new AtomicBoolean(false);
    if (loop.getDispatcher() instanceof DefaultObservationDispatcher dod) {
      dod.register(
          new AgentLoopObserver() {
            @Override
            public void onObservation(
                String sessionId, ObservationType type, String content, Map<String, Object> data) {
              if (type == ObservationType.ERROR) {
                errorObserved.set(true);
              }
            }

            @Override
            public void onUsageRecorded(
                String sessionId, work.ganglia.port.internal.state.TokenUsage usage) {}
          });
    }

    // Abort and run
    signal.abort();
    loop.run("Abort me", context, signal)
        .onComplete(
            testContext.failing(
                err -> {
                  testContext.verify(
                      () -> {
                        assertTrue(err instanceof AgentAbortedException);
                        assertTrue(
                            !errorObserved.get(),
                            "Should NOT publish ERROR observation for AgentAbortedException");
                        testContext.completeNow();
                      });
                }));
  }
}
