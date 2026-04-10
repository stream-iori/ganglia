package work.ganglia.kernel.hook.builtin;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.external.tool.model.ToolInvokeResult;
import work.ganglia.port.internal.hook.AgentInterceptor;
import work.ganglia.port.internal.memory.MemoryStore;
import work.ganglia.port.internal.memory.ObservationCompressor;
import work.ganglia.port.internal.memory.model.CompressionContext;
import work.ganglia.port.internal.memory.model.MemoryCategory;
import work.ganglia.port.internal.memory.model.MemoryEntry;

/**
 * Built-in hook to intercept large tool outputs and reduce them before they reach the context
 * window. The strategy per tool is governed by a {@link ToolOutputPolicy}:
 *
 * <ul>
 *   <li>{@link ToolOutputPolicy.Action#SKIP} — output is passed through unchanged (e.g. {@code
 *       recall_memory}).
 *   <li>{@link ToolOutputPolicy.Action#WRITE_TO_TMP} — full raw output is written to a
 *       session-scoped temporary file; the context message is replaced with a short path +
 *       line-count hint. Used for reproducible tools ({@code read_file}, {@code run_shell_command},
 *       etc.). Falls back to {@link ToolOutputPolicy.Action#TRUNCATE_WITH_HINT} when {@code
 *       projectRoot} is unavailable.
 *   <li>{@link ToolOutputPolicy.Action#TRUNCATE_WITH_HINT} — token-aware truncation with a
 *       re-invocation hint; no file I/O.
 *   <li>{@link ToolOutputPolicy.Action#COMPRESS_AND_STORE} — LLM-summarise and persist the full
 *       content in MemoryStore; falls back to truncation on failure. Used for irreproducible
 *       content.
 * </ul>
 *
 * <p>Handles all {@link ToolInvokeResult.Status} values — not only SUCCESS.
 */
public class ObservationCompressionHook implements AgentInterceptor {
  private static final Logger logger = LoggerFactory.getLogger(ObservationCompressionHook.class);

  private final ObservationCompressor observationCompressor; // may be null
  private final MemoryStore memoryStore; // may be null
  private final TokenAwareTruncator truncator;
  private final ToolOutputPolicy policy;
  private final SessionTmpStore sessionTmpStore; // null when projectRoot unavailable

  public ObservationCompressionHook(
      ObservationCompressor observationCompressor,
      MemoryStore memoryStore,
      TokenAwareTruncator truncator,
      ToolOutputPolicy policy,
      Vertx vertx,
      String projectRoot) {
    this.observationCompressor = observationCompressor;
    this.memoryStore = memoryStore;
    this.truncator = truncator;
    this.policy = policy;
    this.sessionTmpStore =
        (vertx != null && projectRoot != null) ? new SessionTmpStore(vertx, projectRoot) : null;
  }

  /** Convenience constructor with projectRoot — uses default policy. */
  public ObservationCompressionHook(
      ObservationCompressor observationCompressor,
      MemoryStore memoryStore,
      TokenAwareTruncator truncator,
      Vertx vertx,
      String projectRoot) {
    this(
        observationCompressor,
        memoryStore,
        truncator,
        ToolOutputPolicy.defaultPolicy(),
        vertx,
        projectRoot);
  }

  /** Convenience constructor without LLM compression (pure truncation only, no tmp files). */
  public ObservationCompressionHook(TokenAwareTruncator truncator) {
    this(null, null, truncator, ToolOutputPolicy.defaultPolicy(), null, null);
  }

  /** Constructor for tests — inject a pre-built SessionTmpStore. */
  public ObservationCompressionHook(
      ObservationCompressor observationCompressor,
      MemoryStore memoryStore,
      TokenAwareTruncator truncator,
      ToolOutputPolicy policy,
      SessionTmpStore sessionTmpStore) {
    this.observationCompressor = observationCompressor;
    this.memoryStore = memoryStore;
    this.truncator = truncator;
    this.policy = policy;
    this.sessionTmpStore = sessionTmpStore;
  }

  @Override
  public Future<ToolInvokeResult> postToolExecute(
      ToolCall call, ToolInvokeResult result, SessionContext context) {
    if (call == null || result == null) {
      return Future.succeededFuture(result);
    }

    String rawOutput = result.output();
    if (rawOutput == null || rawOutput.isEmpty()) {
      return Future.succeededFuture(result);
    }

    ToolOutputPolicy.Action action = policy.decide(call.toolName());
    return switch (action) {
      case SKIP -> Future.succeededFuture(result);
      case WRITE_TO_TMP -> writeToTmp(call, result, rawOutput, context);
      case TRUNCATE_WITH_HINT -> Future.succeededFuture(truncateWithHint(call, result, rawOutput));
      case COMPRESS_AND_STORE -> {
        // LLM-based compression when available and output exceeds character threshold
        if (observationCompressor != null
            && memoryStore != null
            && observationCompressor.requiresCompression(rawOutput)) {
          yield compressWithLlm(call, result, rawOutput, context);
        }
        // Fallback: token-aware truncation (no-op if within limit)
        yield Future.succeededFuture(truncateResult(call, result, rawOutput));
      }
    };
  }

  // ── WRITE_TO_TMP ─────────────────────────────────────────────────────────

  private Future<ToolInvokeResult> writeToTmp(
      ToolCall call, ToolInvokeResult result, String rawOutput, SessionContext context) {
    if (sessionTmpStore == null) {
      // No projectRoot — degrade gracefully to truncation with hint
      logger.debug(
          "No projectRoot available for tool '{}', falling back to truncate-with-hint.",
          call.toolName());
      return Future.succeededFuture(truncateWithHint(call, result, rawOutput));
    }

    String sessionId = context != null ? context.sessionId() : "unknown";

    return sessionTmpStore
        .store(sessionId, call.id(), call.toolName(), rawOutput)
        .map(hint -> withOutput(result, hint));
  }

  // ── COMPRESS_AND_STORE ───────────────────────────────────────────────────

  private Future<ToolInvokeResult> compressWithLlm(
      ToolCall call, ToolInvokeResult result, String rawOutput, SessionContext context) {
    logger.debug(
        "Intercepted large output from tool '{}', triggering LLM compression.", call.toolName());

    String taskDesc = resolveTaskDesc(context);
    CompressionContext compCtx = new CompressionContext(call.toolName(), taskDesc, 1024);

    return observationCompressor
        .compress(rawOutput, compCtx)
        .compose(
            summary -> {
              String memoryId = UUID.randomUUID().toString().substring(0, 8);
              MemoryEntry entry =
                  new MemoryEntry(
                      memoryId,
                      "Compressed Output: " + call.toolName(),
                      summary,
                      rawOutput,
                      MemoryCategory.OBSERVATION,
                      Collections.emptyList(),
                      Instant.now(),
                      Collections.emptyList());
              return memoryStore
                  .store(entry)
                  .map(
                      v -> {
                        String newOutput =
                            "Output was very long and has been compressed. ID: "
                                + memoryId
                                + ".\nSummary: "
                                + summary
                                + "\nUse recall_memory tool to view full content.";
                        return withOutput(result, newOutput);
                      });
            })
        .recover(
            err -> {
              logger.warn(
                  "Observation compression failed for tool '{}', falling back to truncation",
                  call.toolName(),
                  err);
              return Future.succeededFuture(truncateResult(call, result, rawOutput));
            });
  }

  // ── Truncation helpers ───────────────────────────────────────────────────

  private ToolInvokeResult truncateResult(
      ToolCall call, ToolInvokeResult result, String rawOutput) {
    if (truncator == null) {
      return result;
    }
    String truncated = truncator.truncate(rawOutput, call.toolName());
    if (truncated.equals(rawOutput)) {
      return result; // unchanged content means no truncation needed
    }
    logger.debug(
        "Truncated output from tool '{}' to {} tokens.", call.toolName(), truncator.getMaxTokens());
    return withOutput(result, truncated);
  }

  /**
   * Truncates the output and appends a hint telling the agent it can re-invoke the tool with
   * pagination parameters to read remaining content. Used as fallback for reproducible tools when
   * no tmp store is available.
   */
  private ToolInvokeResult truncateWithHint(
      ToolCall call, ToolInvokeResult result, String rawOutput) {
    if (truncator == null) {
      return result;
    }
    String hint =
        "Re-invoke '"
            + call.toolName()
            + "' with offset/limit parameters to read the remaining content.";
    String truncated = truncator.truncate(rawOutput, call.toolName(), hint);
    if (truncated.equals(rawOutput)) {
      return result; // within limit — no change
    }
    logger.debug(
        "Truncated reproducible output from tool '{}' to {} tokens (with re-run hint).",
        call.toolName(),
        truncator.getMaxTokens());
    return withOutput(result, truncated);
  }

  private static ToolInvokeResult withOutput(ToolInvokeResult original, String newOutput) {
    return new ToolInvokeResult(
            newOutput,
            original.status(),
            original.errorDetails(),
            original.modifiedContext(),
            original.diff(),
            original.metadata())
        .withOutputCapped();
  }

  private static String resolveTaskDesc(SessionContext context) {
    if (context != null
        && context.currentTurn() != null
        && context.currentTurn().userMessage() != null) {
      String content = context.currentTurn().userMessage().content();
      if (content != null && !content.isEmpty()) {
        return content;
      }
    }
    return "Tool execution";
  }
}
