package me.stream.ganglia.core.tools.model;

/**
 * Exception thrown when a tool fails to execute correctly.
 */
public class ToolExecutionException extends RuntimeException {
    private final ToolErrorResult errorResult;

    public ToolExecutionException(ToolErrorResult errorResult) {
        super(errorResult.message());
        this.errorResult = errorResult;
    }

    public ToolErrorResult getErrorResult() {
        return errorResult;
    }
}
