package work.ganglia.kernel.hook.builtin;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;

import work.ganglia.infrastructure.external.tool.model.ToolInvokeResult;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.internal.hook.AgentInterceptor;
import work.ganglia.port.internal.memory.MemoryStore;
import work.ganglia.port.internal.memory.ObservationCompressor;
import work.ganglia.port.internal.memory.model.CompressionContext;
import work.ganglia.port.internal.memory.model.MemoryCategory;
import work.ganglia.port.internal.memory.model.MemoryEntry;

/**
 * Built-in hook to intercept large tool outputs, compress them using an LLM, and store the full
 * content in the MemoryStore for progressive disclosure.
 *
 * <p>Dual-mode operation:
 *
 * <ol>
 *   <li>If {@code observationCompressor} and {@code memoryStore} are provided: LLM-based
 *       compression; on failure falls back to pure truncation.
 *   <li>Otherwise: pure token-aware truncation via {@link TokenAwareTruncator}.
 * </ol>
 *
 * <p>Handles all {@link ToolInvokeResult.Status} values — not only SUCCESS.
 */
public class ObservationCompressionHook implements AgentInterceptor {
  private static final Logger log = LoggerFactory.getLogger(ObservationCompressionHook.class);

  private final ObservationCompressor observationCompressor; // may be null
  private final MemoryStore memoryStore; // may be null
  private final TokenAwareTruncator truncator;

  public ObservationCompressionHook(
      ObservationCompressor observationCompressor,
      MemoryStore memoryStore,
      TokenAwareTruncator truncator) {
    this.observationCompressor = observationCompressor;
    this.memoryStore = memoryStore;
    this.truncator = truncator;
  }

  /** Convenience constructor without LLM compression (pure truncation only). */
  public ObservationCompressionHook(TokenAwareTruncator truncator) {
    this(null, null, truncator);
  }

  @Override
  public Future<ToolInvokeResult> postToolExecute(
      ToolCall call, ToolInvokeResult result, SessionContext context) {
    if (call == null || result == null) {
      return Future.succeededFuture(result);
    }

    if ("recall_memory".equals(call.toolName())) {
      return Future.succeededFuture(result);
    }

    String rawOutput = result.output();
    if (rawOutput == null || rawOutput.isEmpty()) {
      return Future.succeededFuture(result);
    }

    // LLM-based compression takes priority when the output exceeds the character threshold
    if (observationCompressor != null
        && memoryStore != null
        && observationCompressor.requiresCompression(rawOutput)) {
      return compressWithLlm(call, result, rawOutput, context);
    }

    // Token-aware truncation as fallback (no-op if output is within limit)
    return Future.succeededFuture(truncateResult(call, result, rawOutput));
  }

  // -------------------------------------------------------------------------

  private Future<ToolInvokeResult> compressWithLlm(
      ToolCall call, ToolInvokeResult result, String rawOutput, SessionContext context) {
    log.debug(
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
              log.warn(
                  "Observation compression failed for tool '{}', falling back to truncation",
                  call.toolName(),
                  err);
              return Future.succeededFuture(truncateResult(call, result, rawOutput));
            });
  }

  private ToolInvokeResult truncateResult(
      ToolCall call, ToolInvokeResult result, String rawOutput) {
    if (truncator == null) return result;
    String truncated = truncator.truncate(rawOutput, call.toolName());
    if (truncated == rawOutput) return result; // unchanged reference means no truncation needed
    log.debug(
        "Truncated output from tool '{}' to {} tokens.", call.toolName(), truncator.getMaxTokens());
    return withOutput(result, truncated);
  }

  private static ToolInvokeResult withOutput(ToolInvokeResult original, String newOutput) {
    return new ToolInvokeResult(
        newOutput,
        original.status(),
        original.errorDetails(),
        original.modifiedContext(),
        original.diff(),
        original.data());
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
