package work.ganglia.coding.tool;

import java.util.List;
import java.util.Map;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

import work.ganglia.coding.tool.util.DiffGenerator;
import work.ganglia.infrastructure.external.tool.model.ToolInvokeResult;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.external.tool.ToolDefinition;
import work.ganglia.port.external.tool.ToolSet;
import work.ganglia.util.PathMapper;
import work.ganglia.util.PathSanitizer;

/**
 * Facade for surgical file editing (precise replacement), full file writing, and patch application.
 */
public class FileEditTools implements ToolSet {

  private final ReplaceInFileTool replaceInFileTool;
  private final WriteFileTool writeFileTool;
  private final ApplyPatchTool applyPatchTool;

  static Future<Void> validateFileExists(boolean exists, String filePath) {
    if (!exists) {
      return Future.failedFuture(new SecurityException("File not found: " + filePath));
    }
    return Future.succeededFuture();
  }

  public FileEditTools(Vertx vertx) {
    this(vertx, new PathSanitizer());
  }

  public FileEditTools(Vertx vertx, PathMapper pathMapper) {
    DiffGenerator diffGenerator = new DiffGenerator(vertx);
    this.replaceInFileTool = new ReplaceInFileTool(vertx.fileSystem(), pathMapper, diffGenerator);
    this.writeFileTool = new WriteFileTool(vertx.fileSystem(), pathMapper, diffGenerator);
    this.applyPatchTool = new ApplyPatchTool(vertx, vertx.fileSystem(), pathMapper);
  }

  @Override
  public List<ToolDefinition> getDefinitions() {
    return List.of(
        replaceInFileTool.getDefinition(),
        writeFileTool.getDefinition(),
        applyPatchTool.getDefinition());
  }

  @Override
  public Future<ToolInvokeResult> execute(
      ToolCall call,
      SessionContext context,
      work.ganglia.port.internal.state.ExecutionContext executionContext) {
    return execute(call.toolName(), call.arguments(), context, executionContext);
  }

  @Override
  public Future<ToolInvokeResult> execute(
      String toolName,
      Map<String, Object> args,
      SessionContext context,
      work.ganglia.port.internal.state.ExecutionContext executionContext) {
    Future<ToolInvokeResult> result;
    try {
      result =
          switch (toolName) {
            case "replace_in_file" -> replaceInFileTool.execute(args);
            case "write_file" -> writeFileTool.execute(args);
            case "apply_patch" -> applyPatchTool.execute(args);
            default -> Future.succeededFuture(ToolInvokeResult.error("Unknown tool: " + toolName));
          };
    } catch (SecurityException e) {
      return Future.succeededFuture(
          ToolInvokeResult.error("Security/Validation Error: " + e.getMessage()));
    }

    return result.recover(
        err -> {
          if (err instanceof SecurityException) {
            return Future.succeededFuture(ToolInvokeResult.error(err.getMessage()));
          }
          return Future.failedFuture(err);
        });
  }
}
