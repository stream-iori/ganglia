package work.ganglia.infrastructure.external.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.external.tool.model.ToolInvokeResult;
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

  @Test
  void testGetDefinitions_includesTargetParameter() {
    String schema = tools.getDefinitions().get(0).jsonSchema();
    assertTrue(schema.contains("\"target\""), "Schema should include target parameter");
    assertTrue(schema.contains("\"project\""), "Schema should include project option");
    assertTrue(schema.contains("\"user\""), "Schema should include user option");
  }

  @Test
  void testRemember_defaultTarget_usesProjectTopic(VertxTestContext testContext) {
    List<String> appendedTopics = new ArrayList<>();
    LongTermMemory trackingMemory =
        new LongTermMemory() {
          @Override
          public Future<Void> ensureInitialized(String topic) {
            return Future.succeededFuture();
          }

          @Override
          public Future<Void> append(String topic, String content) {
            appendedTopics.add(topic);
            return Future.succeededFuture();
          }

          @Override
          public Future<String> read(String topic) {
            return Future.succeededFuture("");
          }
        };
    KnowledgeBaseTools trackTools = new KnowledgeBaseTools(null, trackingMemory);

    Map<String, Object> args = new HashMap<>();
    args.put("fact", "some fact");
    // No target specified — should default to project
    trackTools
        .remember(args, null)
        .onComplete(
            testContext.succeeding(
                result ->
                    testContext.verify(
                        () -> {
                          assertEquals(1, appendedTopics.size());
                          assertEquals(LongTermMemory.DEFAULT_TOPIC, appendedTopics.get(0));
                          assertTrue(result.output().contains("project memory"));
                          testContext.completeNow();
                        })));
  }

  @Test
  void testRemember_userTarget_usesUserProfileTopic(VertxTestContext testContext) {
    List<String> appendedTopics = new ArrayList<>();
    LongTermMemory trackingMemory =
        new LongTermMemory() {
          @Override
          public Future<Void> ensureInitialized(String topic) {
            return Future.succeededFuture();
          }

          @Override
          public Future<Void> append(String topic, String content) {
            appendedTopics.add(topic);
            return Future.succeededFuture();
          }

          @Override
          public Future<String> read(String topic) {
            return Future.succeededFuture("");
          }
        };
    KnowledgeBaseTools trackTools = new KnowledgeBaseTools(null, trackingMemory);

    Map<String, Object> args = new HashMap<>();
    args.put("fact", "prefers concise");
    args.put("target", "user");
    trackTools
        .remember(args, null)
        .onComplete(
            testContext.succeeding(
                result ->
                    testContext.verify(
                        () -> {
                          assertEquals(1, appendedTopics.size());
                          assertEquals(LongTermMemory.USER_PROFILE_TOPIC, appendedTopics.get(0));
                          assertTrue(result.output().contains("user memory"));
                          testContext.completeNow();
                        })));
  }
}
