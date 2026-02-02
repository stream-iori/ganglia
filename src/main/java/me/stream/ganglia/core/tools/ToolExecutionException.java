package me.stream.ganglia.core.tools;

import me.stream.ganglia.core.tools.model.ToolErrorResult;

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
