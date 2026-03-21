package work.ganglia.infrastructure.external.tool.model;

/** Represents a structured error from a tool execution. */
public record ToolErrorResult(
    String toolName,
    ErrorType errorType,
    String message,
    Integer exitCode, // May be null if not applicable (e.g. timeout)
    String partialOutput // Captured output before failure
    ) {
  public enum ErrorType {
    TIMEOUT,
    SIZE_LIMIT_EXCEEDED,
    COMMAND_FAILED,
    UNKNOWN
  }
}
