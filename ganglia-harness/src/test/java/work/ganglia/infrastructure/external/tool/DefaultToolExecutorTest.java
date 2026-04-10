package work.ganglia.infrastructure.external.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.BaseGangliaTest;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.external.tool.ToolDefinition;
import work.ganglia.port.external.tool.ToolSet;
import work.ganglia.port.external.tool.model.ToolInvokeResult;
import work.ganglia.port.internal.memory.MemoryStore;
import work.ganglia.port.internal.memory.model.MemoryCategory;
import work.ganglia.port.internal.memory.model.MemoryEntry;
import work.ganglia.stubs.StubExecutionContext;

@ExtendWith(VertxExtension.class)
class DefaultToolExecutorTest extends BaseGangliaTest {

  private DefaultToolExecutor executor;
  private StubExecutionContext execCtx;

  /** Helper to create a minimal MemoryStore mock where recall() returns the given future. */
  private MemoryStore mockMemoryStore(Future<MemoryEntry> recallResult) {
    MemoryStore mock = mock(MemoryStore.class);
    when(mock.store(any())).thenReturn(Future.succeededFuture());
    when(mock.search(any())).thenReturn(Future.succeededFuture(List.of()));
    when(mock.getRecentIndex(any(Integer.class))).thenReturn(Future.succeededFuture(List.of()));
    when(mock.recall(any())).thenReturn(recallResult);
    return mock;
  }

  private MemoryEntry sampleEntry() {
    return new MemoryEntry(
        "id1",
        "Title",
        "summary",
        "Content",
        MemoryCategory.UNKNOWN,
        List.of(),
        java.time.Instant.now(),
        List.of());
  }

  @BeforeEach
  void setUp(Vertx vertx) {
    execCtx = new StubExecutionContext();

    ToolSet mockEchoToolSet = mock(ToolSet.class);
    when(mockEchoToolSet.getDefinitions())
        .thenReturn(
            List.of(
                new ToolDefinition(
                    "echo",
                    "Echo a message",
                    """
                    {
                      "type": "object",
                      "properties": {
                        "text": { "type": "string" }
                      },
                      "required": ["text"]
                    }
                    """)));
    when(mockEchoToolSet.execute(any(ToolCall.class), any(), any()))
        .thenAnswer(
            invocation -> {
              ToolCall call = invocation.getArgument(0);
              return Future.succeededFuture(
                  ToolInvokeResult.success("echo: " + call.arguments().get("text")));
            });
    when(mockEchoToolSet.execute(eq("echo"), any(), any(), any()))
        .thenAnswer(
            invocation -> {
              Map<String, Object> args = invocation.getArgument(1);
              return Future.succeededFuture(ToolInvokeResult.success("echo: " + args.get("text")));
            });
    when(mockEchoToolSet.isAvailableFor(any())).thenReturn(true);

    executor = new DefaultToolExecutor(null, List.of(mockEchoToolSet));
  }

  @Test
  void testKnownToolExecutes(VertxTestContext testContext) {
    ToolCall call = new ToolCall("c1", "echo", Map.of("text", "hello"));
    assertFutureSuccess(
        executor.execute(call, null, execCtx),
        testContext,
        result -> {
          assertEquals(ToolInvokeResult.Status.SUCCESS, result.status());
          assertTrue(result.output().contains("hello"));
        });
  }

  @Test
  void testUnknownToolReturnsError(VertxTestContext testContext) {
    ToolCall call = new ToolCall("c2", "unknown_tool", Map.of());
    assertFutureSuccess(
        executor.execute(call, null, execCtx),
        testContext,
        result -> {
          assertEquals(ToolInvokeResult.Status.ERROR, result.status());
          assertTrue(result.output().contains("Unknown tool"));
        });
  }

  @Test
  void testGetAvailableToolsIncludesAllToolSets() {
    List<work.ganglia.port.external.tool.ToolDefinition> defs = executor.getAvailableTools(null);
    assertFalse(defs.isEmpty());
    assertTrue(defs.stream().anyMatch(d -> "echo".equals(d.name())));
  }

  @Test
  void testNoToolSetsReturnsError(VertxTestContext testContext) {
    DefaultToolExecutor empty = new DefaultToolExecutor(null, List.of());
    ToolCall call = new ToolCall("c3", "bash", Map.of());
    assertFutureSuccess(
        empty.execute(call, null, execCtx),
        testContext,
        result -> {
          assertEquals(ToolInvokeResult.Status.ERROR, result.status());
        });
  }

  @Test
  void testInteractionToolsAskSelection(Vertx vertx, VertxTestContext testContext) {
    InteractionTools interactionTools = new InteractionTools(vertx);
    ToolCall call =
        new ToolCall(
            "c4",
            "ask_selection",
            Map.of(
                "questions",
                List.of(Map.of("question", "Choose?", "type", "text", "header", "Q"))));
    assertFutureSuccess(
        interactionTools.execute(call, null, execCtx),
        testContext,
        result -> {
          assertEquals(ToolInvokeResult.Status.INTERRUPT, result.status());
        });
  }

  @Test
  void testInteractionToolsUnknownTool(Vertx vertx, VertxTestContext testContext) {
    InteractionTools interactionTools = new InteractionTools(vertx);
    ToolCall call = new ToolCall("c5", "no_such_tool", Map.of());
    assertFutureSuccess(
        interactionTools.execute(call, null, execCtx),
        testContext,
        result -> {
          assertEquals(ToolInvokeResult.Status.ERROR, result.status());
        });
  }

  @Test
  void testInteractionToolsEmptyQuestions(Vertx vertx, VertxTestContext testContext) {
    InteractionTools interactionTools = new InteractionTools(vertx);
    ToolCall call = new ToolCall("c6", "ask_selection", Map.of("questions", List.of()));
    assertFutureSuccess(
        interactionTools.execute(call, null, execCtx),
        testContext,
        result -> {
          assertEquals(ToolInvokeResult.Status.ERROR, result.status());
        });
  }

  @Test
  void testRecallMemoryToolsMissingId(VertxTestContext testContext) {
    RecallMemoryTools tool =
        new RecallMemoryTools(mockMemoryStore(Future.failedFuture("not found")));
    ToolCall call = new ToolCall("c7", "recall_memory", Map.of());
    assertFutureSuccess(
        tool.execute(call, null, execCtx),
        testContext,
        result -> {
          assertEquals(ToolInvokeResult.Status.ERROR, result.status());
        });
  }

  @Test
  void testRecallMemoryToolsSuccess(VertxTestContext testContext) {
    RecallMemoryTools tool =
        new RecallMemoryTools(mockMemoryStore(Future.succeededFuture(sampleEntry())));
    ToolCall call = new ToolCall("c8", "recall_memory", Map.of("id", "id1"));
    assertFutureSuccess(
        tool.execute(call, null, execCtx),
        testContext,
        result -> {
          assertEquals(ToolInvokeResult.Status.SUCCESS, result.status());
          assertTrue(result.output().contains("Title"));
        });
  }

  @Test
  void testRecallMemoryToolsFailure(VertxTestContext testContext) {
    RecallMemoryTools tool =
        new RecallMemoryTools(
            mockMemoryStore(Future.failedFuture(new RuntimeException("Storage error"))));
    ToolCall call = new ToolCall("c9", "recall_memory", Map.of("id", "missing-id"));
    assertFutureSuccess(
        tool.execute(call, null, execCtx),
        testContext,
        result -> {
          assertEquals(ToolInvokeResult.Status.ERROR, result.status());
        });
  }

  @Test
  void testRecallMemoryUnknownTool(VertxTestContext testContext) {
    RecallMemoryTools tool =
        new RecallMemoryTools(mockMemoryStore(Future.failedFuture("not found")));
    ToolCall call = new ToolCall("c10", "other_tool", Map.of());
    assertFutureSuccess(
        tool.execute(call, null, execCtx),
        testContext,
        result -> {
          assertEquals(ToolInvokeResult.Status.ERROR, result.status());
        });
  }
}
