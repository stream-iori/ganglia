package work.ganglia.kernel.hook;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.BaseGangliaTest;
import work.ganglia.kernel.hook.builtin.ObservationCompressionHook;
import work.ganglia.kernel.hook.builtin.SessionTmpStore;
import work.ganglia.kernel.hook.builtin.TokenAwareTruncator;
import work.ganglia.kernel.hook.builtin.ToolOutputPolicy;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.external.tool.model.ToolInvokeResult;
import work.ganglia.util.Constants;
import work.ganglia.util.TokenCounter;

/** Tests for SessionTmpStore and the WRITE_TO_TMP hook path. */
class SessionTmpStoreTest extends BaseGangliaTest {

  @TempDir Path tempDir;

  private static final String SESSION_ID = "test-session-123";
  private static final String TOOL_CALL_ID = "call-abc";
  private static final String TOOL_NAME = "read_file";
  private static final String MULTI_LINE_OUTPUT = "line1\nline2\nline3\nline4\nline5\n";

  // ── SessionTmpStore unit tests ────────────────────────────────────────────

  @Test
  @DisplayName("store() writes raw output to session tmp file")
  void store_writesRawOutputToFile(Vertx vertx, VertxTestContext testContext) {
    SessionTmpStore store = new SessionTmpStore(vertx, tempDir.toString());

    store
        .store(SESSION_ID, TOOL_CALL_ID, TOOL_NAME, MULTI_LINE_OUTPUT)
        .onComplete(
            testContext.succeeding(
                hint -> {
                  testContext.verify(
                      () -> {
                        // File must exist at the expected path
                        Path expectedFile =
                            tempDir
                                .resolve(Constants.DIR_TMP)
                                .resolve(SESSION_ID)
                                .resolve(TOOL_CALL_ID + ".txt");
                        assertTrue(
                            Files.exists(expectedFile),
                            "Tmp file must be created at: " + expectedFile);

                        String content;
                        try {
                          content = Files.readString(expectedFile);
                        } catch (IOException e) {
                          throw new AssertionError("Cannot read tmp file", e);
                        }
                        assertEquals(MULTI_LINE_OUTPUT, content, "File must contain raw output");
                      });
                  testContext.completeNow();
                }));
  }

  @Test
  @DisplayName("store() returns hint containing file path and line count")
  void store_returnsHintWithPathAndLineCount(Vertx vertx, VertxTestContext testContext) {
    SessionTmpStore store = new SessionTmpStore(vertx, tempDir.toString());

    store
        .store(SESSION_ID, TOOL_CALL_ID, TOOL_NAME, MULTI_LINE_OUTPUT)
        .onComplete(
            testContext.succeeding(
                hint -> {
                  testContext.verify(
                      () -> {
                        assertNotNull(hint);
                        assertTrue(hint.contains(TOOL_NAME), "Hint must mention tool name");
                        assertTrue(
                            hint.contains(SESSION_ID) || hint.contains(TOOL_CALL_ID),
                            "Hint must reference the file path");
                        // 5 newlines → 6 lines (countLines adds 1 for non-empty text)
                        assertTrue(hint.contains("6 lines"), "Hint must include line count");
                        assertTrue(
                            hint.contains("read_file"),
                            "Hint must suggest using read_file to read the file");
                      });
                  testContext.completeNow();
                }));
  }

  @Test
  @DisplayName("store() creates separate files for different sessions")
  void store_separateFilesPerSession(Vertx vertx, VertxTestContext testContext) {
    SessionTmpStore store = new SessionTmpStore(vertx, tempDir.toString());
    String session2 = "other-session-456";

    store
        .store(SESSION_ID, TOOL_CALL_ID, TOOL_NAME, "output-A")
        .compose(h1 -> store.store(session2, TOOL_CALL_ID, TOOL_NAME, "output-B"))
        .onComplete(
            testContext.succeeding(
                ignored -> {
                  testContext.verify(
                      () -> {
                        Path file1 =
                            tempDir
                                .resolve(Constants.DIR_TMP)
                                .resolve(SESSION_ID)
                                .resolve(TOOL_CALL_ID + ".txt");
                        Path file2 =
                            tempDir
                                .resolve(Constants.DIR_TMP)
                                .resolve(session2)
                                .resolve(TOOL_CALL_ID + ".txt");
                        assertTrue(Files.exists(file1));
                        assertTrue(Files.exists(file2));
                        assertNotEquals(file1, file2, "Each session must have its own directory");
                      });
                  testContext.completeNow();
                }));
  }

  // ── Static helper tests (no Vertx needed) ────────────────────────────────

  @Test
  @DisplayName("countLines returns 1 for single-line text")
  void countLines_singleLine() {
    assertEquals(1, SessionTmpStore.countLines("hello"));
  }

  @Test
  @DisplayName("countLines returns correct count for multi-line text")
  void countLines_multiLine() {
    assertEquals(6, SessionTmpStore.countLines(MULTI_LINE_OUTPUT)); // 5 \n → 6 segments
  }

  @Test
  @DisplayName("countLines returns 0 for null/empty")
  void countLines_empty() {
    assertEquals(0, SessionTmpStore.countLines(null));
    assertEquals(0, SessionTmpStore.countLines(""));
  }

  @Test
  @DisplayName("buildHint contains tool name, path, line count, and read_file suggestion")
  void buildHint_format() {
    String hint = SessionTmpStore.buildHint("read_file", "/tmp/sess/call.txt", 42);
    assertTrue(hint.contains("read_file"));
    assertTrue(hint.contains("/tmp/sess/call.txt"));
    assertTrue(hint.contains("42 lines"));
    assertTrue(hint.contains("read_file(\"/tmp/sess/call.txt\""));
  }

  // ── Hook integration: WRITE_TO_TMP with real SessionTmpStore ─────────────

  @Test
  @DisplayName("Hook routes read_file to WRITE_TO_TMP and writes file")
  void hook_writesToTmpFile_forReadFileTool(Vertx vertx, VertxTestContext testContext) {
    SessionTmpStore store = new SessionTmpStore(vertx, tempDir.toString());
    TokenAwareTruncator truncator = new TokenAwareTruncator(new TokenCounter(), 10);
    // Use WRITE_TO_TMP policy + inject our store via package-private constructor
    ObservationCompressionHook hook =
        new ObservationCompressionHook(
            null, null, truncator, ToolOutputPolicy.defaultPolicy(), store);

    String bigOutput = "line\n".repeat(50);
    ToolCall call = new ToolCall(TOOL_CALL_ID, TOOL_NAME, java.util.Map.of("path", "big.txt"));
    ToolInvokeResult result = ToolInvokeResult.success(bigOutput);
    SessionContext ctx = createSessionContext();

    hook.postToolExecute(call, result, ctx)
        .onComplete(
            testContext.succeeding(
                intercepted -> {
                  testContext.verify(
                      () -> {
                        assertTrue(intercepted.isOutputCapped(), "Result must be marked capped");
                        assertTrue(
                            intercepted.output().contains("lines"),
                            "Output must be replaced with hint containing line count");
                        assertTrue(
                            intercepted.output().contains(TOOL_NAME),
                            "Hint must mention tool name");

                        // The tmp file must exist under the session directory
                        Path expectedFile =
                            tempDir
                                .resolve(Constants.DIR_TMP)
                                .resolve(ctx.sessionId())
                                .resolve(TOOL_CALL_ID + ".txt");
                        assertTrue(
                            Files.exists(expectedFile),
                            "Tmp file must be written to session directory");
                      });
                  testContext.completeNow();
                }));
  }

  @Test
  @DisplayName("Hook falls back to TRUNCATE_WITH_HINT when no SessionTmpStore available")
  void hook_fallsBackToTruncation_whenNoTmpStore(VertxTestContext testContext) {
    // Convenience constructor: no vertx, no projectRoot → sessionTmpStore = null
    TokenAwareTruncator truncator = new TokenAwareTruncator(new TokenCounter(), 10);
    ObservationCompressionHook hook = new ObservationCompressionHook(truncator);

    String bigOutput = "word ".repeat(100);
    ToolCall call = new ToolCall("c1", "read_file", java.util.Map.of());
    ToolInvokeResult result = ToolInvokeResult.success(bigOutput);

    hook.postToolExecute(call, result, createSessionContext())
        .onComplete(
            testContext.succeeding(
                intercepted -> {
                  testContext.verify(
                      () -> {
                        assertTrue(intercepted.isOutputCapped());
                        assertTrue(
                            intercepted.output().contains("[TRUNCATED"),
                            "Fallback must produce TRUNCATED notice");
                        assertTrue(
                            intercepted.output().contains("Re-invoke"),
                            "Fallback must include re-invocation hint");
                      });
                  testContext.completeNow();
                }));
  }
}
