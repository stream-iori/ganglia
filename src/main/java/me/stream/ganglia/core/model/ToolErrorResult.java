package me.stream.ganglia.core.model;

public record ToolErrorResult(
    String toolName,
    ErrorType errorType,
    String message,
    Integer exitCode,
    String partialOutput
) {
    public enum ErrorType {
        TIMEOUT,
        SIZE_LIMIT_EXCEEDED,
        COMMAND_FAILED,
        UNKNOWN
    }
}
