package work.ganglia.infrastructure.external.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.infrastructure.external.tool.model.ToolInvokeResult;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.external.tool.ToolDefinition;
import work.ganglia.port.external.tool.ToolSet;
import work.ganglia.port.internal.memory.MemoryStore;
import work.ganglia.port.internal.memory.model.MemoryCategory;
import work.ganglia.port.internal.memory.model.MemoryEntry;
import work.ganglia.port.internal.memory.model.MemoryIndexItem;
import work.ganglia.port.internal.memory.model.MemoryQuery;
import work.ganglia.stubs.StubExecutionContext;

@ExtendWith(VertxExtension.class)
class DefaultToolExecutorTest {

  private DefaultToolExecutor executor;
  private StubExecutionContext execCtx;

  /** Helper to create a minimal MemoryStore stub where recall() returns the given future. */
  private MemoryStore memoryStore(Future<MemoryEntry> recallResult) {
    return new MemoryStore() {
      @Override
      public Future<Void> store(MemoryEntry entry) {
        return Future.succeededFuture();
      }

      @Override
      public Future<List<MemoryEntry>> search(MemoryQuery query) {
        return Future.succeededFuture(List.of());
      }

      @Override
      public Future<List<MemoryIndexItem>> getRecentIndex(int limit) {
        return Future.succeededFuture(List.of());
      }

      @Override
      public Future<MemoryEntry> recall(String id) {
        return recallResult;
      }
    };
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

    ToolSet echoToolSet =
        new ToolSet() {
          @Override
          public List<ToolDefinition> getDefinitions() {
            return List.of(
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
                    """));
          }

          @Override
          public Future<ToolInvokeResult> execute(
              String toolName,
              Map<String, Object> args,
              SessionContext ctx,
              work.ganglia.port.internal.state.ExecutionContext execCtx) {
            return Future.succeededFuture(ToolInvokeResult.success("echo: " + args.get("text")));
          }
        };

    executor = new DefaultToolExecutor(null, List.of(echoToolSet));
  }

  @Test
  void testKnownToolExecutes(VertxTestContext testContext) {
    ToolCall call = new ToolCall("c1", "echo", Map.of("text", "hello"));
    executor
        .execute(call, null, execCtx)
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertEquals(ToolInvokeResult.Status.SUCCESS, result.status());
                        assertTrue(result.output().contains("hello"));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testUnknownToolReturnsError(VertxTestContext testContext) {
    ToolCall call = new ToolCall("c2", "unknown_tool", Map.of());
    executor
        .execute(call, null, execCtx)
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertEquals(ToolInvokeResult.Status.ERROR, result.status());
                        assertTrue(result.output().contains("Unknown tool"));
                        testContext.completeNow();
                      });
                }));
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
    empty
        .execute(call, null, execCtx)
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertEquals(ToolInvokeResult.Status.ERROR, result.status());
                        testContext.completeNow();
                      });
                }));
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
    interactionTools
        .execute(call, null, execCtx)
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertEquals(ToolInvokeResult.Status.INTERRUPT, result.status());
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testInteractionToolsUnknownTool(Vertx vertx, VertxTestContext testContext) {
    InteractionTools interactionTools = new InteractionTools(vertx);
    ToolCall call = new ToolCall("c5", "no_such_tool", Map.of());
    interactionTools
        .execute(call, null, execCtx)
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertEquals(ToolInvokeResult.Status.ERROR, result.status());
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testInteractionToolsEmptyQuestions(Vertx vertx, VertxTestContext testContext) {
    InteractionTools interactionTools = new InteractionTools(vertx);
    ToolCall call = new ToolCall("c6", "ask_selection", Map.of("questions", List.of()));
    interactionTools
        .execute(call, null, execCtx)
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertEquals(ToolInvokeResult.Status.ERROR, result.status());
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testRecallMemoryToolsMissingId(VertxTestContext testContext) {
    RecallMemoryTools tool = new RecallMemoryTools(memoryStore(Future.failedFuture("not found")));
    ToolCall call = new ToolCall("c7", "recall_memory", Map.of());
    tool.execute(call, null, execCtx)
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertEquals(ToolInvokeResult.Status.ERROR, result.status());
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testRecallMemoryToolsSuccess(VertxTestContext testContext) {
    RecallMemoryTools tool =
        new RecallMemoryTools(memoryStore(Future.succeededFuture(sampleEntry())));
    ToolCall call = new ToolCall("c8", "recall_memory", Map.of("id", "id1"));
    tool.execute(call, null, execCtx)
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertEquals(ToolInvokeResult.Status.SUCCESS, result.status());
                        assertTrue(result.output().contains("Title"));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testRecallMemoryToolsFailure(VertxTestContext testContext) {
    RecallMemoryTools tool =
        new RecallMemoryTools(
            memoryStore(Future.failedFuture(new RuntimeException("Storage error"))));
    ToolCall call = new ToolCall("c9", "recall_memory", Map.of("id", "missing-id"));
    tool.execute(call, null, execCtx)
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertEquals(ToolInvokeResult.Status.ERROR, result.status());
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testRecallMemoryUnknownTool(VertxTestContext testContext) {
    RecallMemoryTools tool = new RecallMemoryTools(memoryStore(Future.failedFuture("not found")));
    ToolCall call = new ToolCall("c10", "other_tool", Map.of());
    tool.execute(call, null, execCtx)
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertEquals(ToolInvokeResult.Status.ERROR, result.status());
                        testContext.completeNow();
                      });
                }));
  }
}
