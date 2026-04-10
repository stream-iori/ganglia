package work.ganglia.kernel.task;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.BaseGangliaTest;
import work.ganglia.kernel.loop.AgentLoop;
import work.ganglia.kernel.loop.AgentLoopFactory;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.internal.state.AgentSignal;

@ExtendWith(VertxExtension.class)
class SubAgentTaskTest extends BaseGangliaTest {

  private final AtomicReference<String> capturedPrompt = new AtomicReference<>();

  private AgentLoopFactory loopFactory;

  @BeforeEach
  void setUp(Vertx vertx) {
    setUpBase(vertx);
    capturedPrompt.set(null);

    loopFactory =
        () ->
            new AgentLoop() {
              @Override
              public Future<String> run(
                  String userInput, SessionContext context, AgentSignal signal) {
                capturedPrompt.set(userInput);
                return Future.succeededFuture("Sub-agent result");
              }

              @Override
              public Future<String> resume(
                  String askId, String toolOutput, SessionContext context, AgentSignal signal) {
                return Future.succeededFuture("resumed");
              }

              @Override
              public void stop(String sessionId) {}
            };
  }

  @Test
  void execute_withMissionContextInMetadata_prependsMissionPrefix(VertxTestContext testContext) {
    ToolCall call =
        new ToolCall(
            "c1", "sub_agent", Map.of("task", "Investigate the bug", "persona", "INVESTIGATOR"));
    SubAgentTask task = new SubAgentTask(call, loopFactory);

    // Create context with mission_context in metadata
    SessionContext ctx =
        new SessionContext(
            "test-session",
            null,
            null,
            Map.of("mission_context", "Fix the critical login bug"),
            null,
            null,
            null);

    assertFutureSuccess(
        task.execute(ctx, null),
        testContext,
        result -> {
          String prompt = capturedPrompt.get();
          assertNotNull(prompt, "Child loop should have been called");
          assertTrue(
              prompt.startsWith("MISSION: Fix the critical login bug"),
              "Prompt should start with MISSION prefix, got: " + prompt);
          assertTrue(prompt.contains("TASK: Investigate the bug"), "Prompt should contain TASK");
        });
  }

  @Test
  void execute_withoutMissionContext_usesTaskOnly(VertxTestContext testContext) {
    ToolCall call =
        new ToolCall(
            "c1", "sub_agent", Map.of("task", "Simple investigation", "persona", "GENERAL"));
    SubAgentTask task = new SubAgentTask(call, loopFactory);

    SessionContext ctx = createSessionContext("test-session");

    assertFutureSuccess(
        task.execute(ctx, null),
        testContext,
        result -> {
          String prompt = capturedPrompt.get();
          assertNotNull(prompt);
          assertTrue(
              prompt.startsWith("TASK: Simple investigation"),
              "Prompt should start with TASK, got: " + prompt);
          assertFalse(prompt.contains("MISSION:"), "Should not contain MISSION prefix");
        });
  }
}
