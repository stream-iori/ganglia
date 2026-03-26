package work.ganglia.infrastructure.internal.prompt;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.kernel.task.AgentTaskFactory;
import work.ganglia.kernel.task.DefaultAgentTaskFactory;
import work.ganglia.port.chat.Message;
import work.ganglia.port.chat.Role;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.chat.Turn;
import work.ganglia.port.external.llm.ModelOptions;
import work.ganglia.stubs.StubToolExecutor;
import work.ganglia.util.TokenCounter;

@ExtendWith(VertxExtension.class)
class StandardPromptEngineTest {

  @Test
  void testPruneHistory() {
    TokenCounter counter = new TokenCounter();
    SessionContext context =
        new SessionContext(
            "sid",
            Collections.emptyList(),
            null,
            Collections.emptyMap(),
            Collections.emptyList(),
            null);

    Message m1 = Message.user("Msg 1");
    Message m2 = Message.assistant("Msg 2");
    Message m3 = Message.user("Msg 3");

    // These steps are added to the CURRENT turn
    context = context.addStep(m1).addStep(m2).addStep(m3);

    // Prune to 1 token (current turn is ALWAYS kept entirely)
    List<Message> pruned = context.getPrunedHistory(1, counter);
    assertEquals(3, pruned.size());
    assertEquals("Msg 1", pruned.get(0).content());
    assertEquals("Msg 3", pruned.get(2).content());
  }

  @Test
  void testCapToolMessagesInPrepareRequest(Vertx vertx, VertxTestContext testContext) {
    // Build a very long TOOL message that exceeds 4000 tokens
    String longToolOutput = "word ".repeat(5000); // ~5000 tokens
    Message userMsg = Message.user("do something");
    Message assistantMsg = Message.assistant("calling tool", List.of());
    Message toolMsg = Message.tool("call-1", "bash", longToolOutput);

    Turn turn = Turn.newTurn("t1", userMsg);
    turn = turn.withStep(assistantMsg).withStep(toolMsg);

    TokenCounter counter = new TokenCounter();
    AgentTaskFactory taskFactory =
        new DefaultAgentTaskFactory(() -> null, new StubToolExecutor(), null, null, null);
    StandardPromptEngine engine =
        new StandardPromptEngine(vertx, null, null, taskFactory, counter, null);

    ModelOptions options = new ModelOptions(0.0, 100, "test-model", true);
    SessionContext ctx =
        new SessionContext(
            "sid",
            Collections.emptyList(),
            turn,
            Collections.emptyMap(),
            Collections.emptyList(),
            options);

    engine
        .prepareRequest(ctx, 0)
        .onComplete(
            testContext.succeeding(
                request -> {
                  testContext.verify(
                      () -> {
                        // Find the TOOL message in the prepared request
                        Message toolInRequest =
                            request.messages().stream()
                                .filter(m -> m.role() == Role.TOOL)
                                .findFirst()
                                .orElse(null);
                        assertNotNull(toolInRequest, "TOOL message should be present");
                        assertTrue(
                            toolInRequest.content().contains("[TRUNCATED:"),
                            "Oversized tool output must be truncated");
                        assertTrue(
                            counter.count(toolInRequest.content()) <= 4000 + 50,
                            "Truncated output must be near or below 4000 tokens");
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testSmallToolMessageNotTruncated(Vertx vertx, VertxTestContext testContext) {
    String shortOutput = "small result";
    Message userMsg = Message.user("do something");
    Message assistantMsg = Message.assistant("calling tool", List.of());
    Message toolMsg = Message.tool("call-1", "bash", shortOutput);

    Turn turn = Turn.newTurn("t1", userMsg);
    turn = turn.withStep(assistantMsg).withStep(toolMsg);

    TokenCounter counter = new TokenCounter();
    AgentTaskFactory taskFactory =
        new DefaultAgentTaskFactory(() -> null, new StubToolExecutor(), null, null, null);
    StandardPromptEngine engine =
        new StandardPromptEngine(vertx, null, null, taskFactory, counter, null);

    ModelOptions options = new ModelOptions(0.0, 100, "test-model", true);
    SessionContext ctx =
        new SessionContext(
            "sid",
            Collections.emptyList(),
            turn,
            Collections.emptyMap(),
            Collections.emptyList(),
            options);

    engine
        .prepareRequest(ctx, 0)
        .onComplete(
            testContext.succeeding(
                request -> {
                  testContext.verify(
                      () -> {
                        Message toolInRequest =
                            request.messages().stream()
                                .filter(m -> m.role() == Role.TOOL)
                                .findFirst()
                                .orElse(null);
                        assertNotNull(toolInRequest);
                        assertEquals(
                            shortOutput,
                            toolInRequest.content(),
                            "Small tool output must not be modified");
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testPrepareRequest(Vertx vertx, VertxTestContext testContext) {
    StubToolExecutor toolExecutor = new StubToolExecutor(); // Returns empty list by default
    TokenCounter counter = new TokenCounter();
    AgentTaskFactory taskFactory =
        new DefaultAgentTaskFactory(() -> null, new StubToolExecutor(), null, null, null);
    StandardPromptEngine engine =
        new StandardPromptEngine(vertx, null, null, taskFactory, counter, null);

    ModelOptions options = new ModelOptions(0.0, 100, "test-model", true);
    SessionContext context =
        new SessionContext(
            "sid",
            Collections.emptyList(),
            null,
            Collections.emptyMap(),
            Collections.emptyList(),
            options);

    engine
        .prepareRequest(context, 0)
        .onComplete(
            testContext.succeeding(
                request -> {
                  assertEquals(1, request.messages().size());
                  assertEquals(Role.SYSTEM, request.messages().get(0).role());
                  assertEquals("test-model", request.options().modelName());
                  testContext.completeNow();
                }));
  }
}
