package work.ganglia.infrastructure.external.tool;

import work.ganglia.infrastructure.external.tool.model.ToolErrorResult;

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
