package work.ganglia.kernel.hook.builtin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.infrastructure.external.tool.model.ToolInvokeResult;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.internal.memory.MemoryStore;
import work.ganglia.port.internal.memory.ObservationCompressor;
import work.ganglia.port.internal.memory.model.CompressionContext;
import work.ganglia.util.TokenCounter;

@ExtendWith(VertxExtension.class)
class ObservationCompressionHookTest {

  private ObservationCompressor compressor;
  private MemoryStore store;
  private TokenAwareTruncator truncator;
  private ObservationCompressionHook hook;
  private SessionContext context;

  @BeforeEach
  void setUp() {
    compressor = mock(ObservationCompressor.class);
    store = mock(MemoryStore.class);
    truncator = new TokenAwareTruncator(new TokenCounter(), 50);
    hook = new ObservationCompressionHook(compressor, store, truncator);
    context =
        new SessionContext(
            "test",
            Collections.emptyList(),
            null,
            Collections.emptyMap(),
            Collections.emptyList(),
            null);
  }

  @Test
  void testCompressionTriggered(VertxTestContext testContext) {
    String rawOutput = "long output".repeat(100);
    ToolCall call = new ToolCall("c1", "ls", Map.of());
    ToolInvokeResult result = ToolInvokeResult.success(rawOutput);

    when(compressor.requiresCompression(rawOutput)).thenReturn(true);
    when(compressor.compress(eq(rawOutput), any(CompressionContext.class)))
        .thenReturn(Future.succeededFuture("summary"));
    when(store.store(any())).thenReturn(Future.succeededFuture());

    hook.postToolExecute(call, result, context)
        .onComplete(
            testContext.succeeding(
                hookedResult -> {
                  testContext.verify(
                      () -> {
                        assertTrue(hookedResult.output().contains("compressed"));
                        assertTrue(hookedResult.output().contains("summary"));
                        verify(store, times(1)).store(any());
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testNoCompressionForSmallOutput(VertxTestContext testContext) {
    String rawOutput = "short";
    ToolCall call = new ToolCall("c1", "ls", Map.of());
    ToolInvokeResult result = ToolInvokeResult.success(rawOutput);

    when(compressor.requiresCompression(rawOutput)).thenReturn(false);

    hook.postToolExecute(call, result, context)
        .onComplete(
            testContext.succeeding(
                hookedResult -> {
                  testContext.verify(
                      () -> {
                        assertEquals(rawOutput, hookedResult.output());
                        verify(compressor, never()).compress(anyString(), any());
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testSkipRecallMemoryTool(VertxTestContext testContext) {
    String rawOutput = "long content".repeat(100);
    ToolCall call = new ToolCall("c1", "recall_memory", Map.of("id", "123"));
    ToolInvokeResult result = ToolInvokeResult.success(rawOutput);

    // Even if it's long, recall_memory should NOT be compressed
    hook.postToolExecute(call, result, context)
        .onComplete(
            testContext.succeeding(
                hookedResult -> {
                  testContext.verify(
                      () -> {
                        assertEquals(rawOutput, hookedResult.output());
                        verify(compressor, never()).requiresCompression(anyString());
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testErrorStatusOutputIsTruncated(VertxTestContext testContext) {
    // ERROR status with very long output should still be truncated
    String rawOutput = "error detail ".repeat(200); // > 50 tokens
    ToolCall call = new ToolCall("c1", "bash", Map.of());
    ToolInvokeResult result = ToolInvokeResult.error(rawOutput);

    when(compressor.requiresCompression(rawOutput)).thenReturn(false);

    hook.postToolExecute(call, result, context)
        .onComplete(
            testContext.succeeding(
                hookedResult -> {
                  testContext.verify(
                      () -> {
                        assertTrue(
                            hookedResult.output().contains("[TRUNCATED:"),
                            "Long error output should be truncated");
                        assertEquals(
                            ToolInvokeResult.Status.ERROR,
                            hookedResult.status(),
                            "Status must remain ERROR");
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testPureTruncationWhenNoLlmCompressor(VertxTestContext testContext) {
    ObservationCompressionHook noLlmHook = new ObservationCompressionHook(truncator);
    String rawOutput = "word ".repeat(200); // > 50 tokens
    ToolCall call = new ToolCall("c1", "cat", Map.of());
    ToolInvokeResult result = ToolInvokeResult.success(rawOutput);

    noLlmHook
        .postToolExecute(call, result, context)
        .onComplete(
            testContext.succeeding(
                hookedResult -> {
                  testContext.verify(
                      () -> {
                        assertTrue(
                            hookedResult.output().contains("[TRUNCATED:"),
                            "Output must be truncated by TokenAwareTruncator");
                        assertFalse(
                            hookedResult.output().contains("recall_memory"),
                            "Pure truncation must not mention recall_memory");
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testLlmFailureFallsBackToTruncation(VertxTestContext testContext) {
    String rawOutput = "data ".repeat(200); // > 50 tokens
    ToolCall call = new ToolCall("c1", "grep", Map.of());
    ToolInvokeResult result = ToolInvokeResult.success(rawOutput);

    when(compressor.requiresCompression(rawOutput)).thenReturn(true);
    when(compressor.compress(eq(rawOutput), any(CompressionContext.class)))
        .thenReturn(Future.failedFuture(new RuntimeException("LLM unavailable")));

    hook.postToolExecute(call, result, context)
        .onComplete(
            testContext.succeeding(
                hookedResult -> {
                  testContext.verify(
                      () -> {
                        assertTrue(
                            hookedResult.output().contains("[TRUNCATED:"),
                            "Should fall back to truncation on LLM failure");
                        testContext.completeNow();
                      });
                }));
  }
}
