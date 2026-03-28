package work.ganglia.infrastructure.external.tool.model;

import java.util.HashMap;
import java.util.Map;

import work.ganglia.port.chat.SessionContext;

/** Represents the structured result of a tool invocation. */
public record ToolInvokeResult(
    String output,
    Status status,
    ToolErrorResult errorDetails,
    SessionContext modifiedContext, // Optional: if the tool modified the session state
    String diff, // Optional: unified diff of changes
    Map<String, Object> metadata // Optional: extension metadata (flags, structured payloads)
    ) {
  public ToolInvokeResult {
    if (metadata != null) {
      metadata = Map.copyOf(metadata);
    }
  }

  public enum Status {
    SUCCESS, // Tool executed and returned successfully
    ERROR, // Tool executed but returned a logical error (e.g., file not found)
    EXCEPTION, // Framework or system failure (e.g., timeout, limit exceeded)
    INTERRUPT // Tool requires user interaction
  }

  public static ToolInvokeResult success(String output) {
    return new ToolInvokeResult(output, Status.SUCCESS, null, null, null, null);
  }

  public static ToolInvokeResult success(String output, String diff) {
    return new ToolInvokeResult(output, Status.SUCCESS, null, null, diff, null);
  }

  public static ToolInvokeResult success(String output, SessionContext modifiedContext) {
    return new ToolInvokeResult(output, Status.SUCCESS, null, modifiedContext, null, null);
  }

  public static ToolInvokeResult interrupt(String prompt) {
    return new ToolInvokeResult(prompt, Status.INTERRUPT, null, null, null, null);
  }

  public static ToolInvokeResult interrupt(String prompt, Map<String, Object> metadata) {
    return new ToolInvokeResult(prompt, Status.INTERRUPT, null, null, null, metadata);
  }

  public static ToolInvokeResult error(String message) {
    return new ToolInvokeResult(message, Status.ERROR, null, null, null, null);
  }

  public static ToolInvokeResult exception(ToolErrorResult details) {
    return new ToolInvokeResult(details.message(), Status.EXCEPTION, details, null, null, null);
  }

  /** Key stored in {@link #metadata()} to signal that the output has already been size-capped. */
  public static final String KEY_OUTPUT_CAPPED = "__output_capped__";

  /**
   * Returns true if this result's output was already size-capped by the interceptor pipeline (e.g.
   * {@code ObservationCompressionHook}). Consumers such as {@code StandardPromptEngine} should skip
   * further truncation for such results.
   */
  public boolean isOutputCapped() {
    return metadata != null && Boolean.TRUE.equals(metadata.get(KEY_OUTPUT_CAPPED));
  }

  /** Returns a copy of this result with the {@link #KEY_OUTPUT_CAPPED} flag set. */
  public ToolInvokeResult withOutputCapped() {
    Map<String, Object> newMetadata = new HashMap<>();
    if (metadata != null) {
      newMetadata.putAll(metadata);
    }
    newMetadata.put(KEY_OUTPUT_CAPPED, Boolean.TRUE);
    return new ToolInvokeResult(output, status, errorDetails, modifiedContext, diff, newMetadata);
  }
}
