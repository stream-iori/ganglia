package me.stream.ganglia.core.tools.model;

/**
 * Represents the structured result of a tool invocation.
 */
public record ToolInvokeResult(
    String output,
    Status status,
    ToolErrorResult errorDetails
) {
    public enum Status {
        SUCCESS,   // Tool executed and returned successfully
        ERROR,     // Tool executed but returned a logical error (e.g., file not found)
        EXCEPTION  // Framework or system failure (e.g., timeout, limit exceeded)
    }

    public static ToolInvokeResult success(String output) {
        return new ToolInvokeResult(output, Status.SUCCESS, null);
    }

    public static ToolInvokeResult error(String message) {
        return new ToolInvokeResult(message, Status.ERROR, null);
    }

    public static ToolInvokeResult exception(ToolErrorResult details) {
        return new ToolInvokeResult(details.message(), Status.EXCEPTION, details);
    }
}
