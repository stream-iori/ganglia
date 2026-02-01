package me.stream.ganglia.core.tools;

import me.stream.ganglia.core.model.ToolErrorResult;

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
