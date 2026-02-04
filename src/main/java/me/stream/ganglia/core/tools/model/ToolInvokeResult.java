package me.stream.ganglia.core.tools.model;

import me.stream.ganglia.core.model.SessionContext;

/**
 * Represents the structured result of a tool invocation.
 */
public record ToolInvokeResult(
    String output,
    Status status,
    ToolErrorResult errorDetails,
    SessionContext modifiedContext // Optional: if the tool modified the session state
) {
    public enum Status {
        SUCCESS,   // Tool executed and returned successfully
        ERROR,     // Tool executed but returned a logical error (e.g., file not found)
        EXCEPTION, // Framework or system failure (e.g., timeout, limit exceeded)
        INTERRUPT  // Tool requires user interaction
    }

    public static ToolInvokeResult success(String output) {
        return new ToolInvokeResult(output, Status.SUCCESS, null, null);
    }

    public static ToolInvokeResult success(String output, SessionContext modifiedContext) {
        return new ToolInvokeResult(output, Status.SUCCESS, null, modifiedContext);
    }

    public static ToolInvokeResult interrupt(String prompt) {
        return new ToolInvokeResult(prompt, Status.INTERRUPT, null, null);
    }

    public static ToolInvokeResult error(String message) {
        return new ToolInvokeResult(message, Status.ERROR, null, null);
    }

    public static ToolInvokeResult exception(ToolErrorResult details) {
        return new ToolInvokeResult(details.message(), Status.EXCEPTION, details, null);
    }
}