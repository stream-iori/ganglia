package work.ganglia.infrastructure.external.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.infrastructure.external.tool.model.ToolInvokeResult;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.internal.memory.LongTermMemory;
import work.ganglia.stubs.StubExecutionContext;

@ExtendWith(VertxExtension.class)
class KnowledgeBaseToolsTest {

  private KnowledgeBaseTools tools;
  private StubExecutionContext execCtx;

  @BeforeEach
  void setUp(Vertx vertx) {
    execCtx = new StubExecutionContext();
    LongTermMemory stubMemory =
        new LongTermMemory() {
          @Override
          public Future<Void> ensureInitialized(String topic) {
            return Future.succeededFuture();
          }

          @Override
          public Future<Void> append(String topic, String content) {
            return Future.succeededFuture();
          }

          @Override
          public Future<String> read(String topic) {
            return Future.succeededFuture("memory content");
          }
        };
    tools = new KnowledgeBaseTools(vertx, stubMemory);
  }

  @Test
  void testRememberSuccess(VertxTestContext testContext) {
    ToolCall call = new ToolCall("c1", "remember", Map.of("fact", "Use Java 17"));
    tools
        .execute(call, null, execCtx)
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertEquals(ToolInvokeResult.Status.SUCCESS, result.status());
                        assertTrue(result.output().contains("Use Java 17"));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testRememberFailure(Vertx vertx, VertxTestContext testContext) {
    LongTermMemory failingMemory =
        new LongTermMemory() {
          @Override
          public Future<Void> ensureInitialized(String topic) {
            return Future.succeededFuture();
          }

          @Override
          public Future<Void> append(String topic, String content) {
            return Future.failedFuture(new RuntimeException("disk full"));
          }

          @Override
          public Future<String> read(String topic) {
            return Future.succeededFuture("");
          }
        };
    KnowledgeBaseTools failTools = new KnowledgeBaseTools(vertx, failingMemory);
    ToolCall call = new ToolCall("c2", "remember", Map.of("fact", "something"));
    failTools
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
  void testUnknownToolReturnsError(VertxTestContext testContext) {
    ToolCall call = new ToolCall("c3", "no_such_tool", Map.of());
    tools
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
  void testGetDefinitionsNotEmpty() {
    assertTrue(tools.getDefinitions().size() > 0);
    assertEquals("remember", tools.getDefinitions().get(0).name());
  }
}
