package work.ganglia.coding.tool;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.ganglia.infrastructure.external.tool.model.ToolInvokeResult;
import work.ganglia.port.external.tool.ToolDefinition;
import work.ganglia.util.PathMapper;
import work.ganglia.util.VertxProcess;

/** Apply a unified diff patch to a file. */
class ApplyPatchTool {
  private static final Logger logger = LoggerFactory.getLogger(ApplyPatchTool.class);

  private final Vertx vertx;
  private final FileSystem fileSystem;
  private final PathMapper pathMapper;

  ApplyPatchTool(Vertx vertx, FileSystem fileSystem, PathMapper pathMapper) {
    this.vertx = vertx;
    this.fileSystem = fileSystem;
    this.pathMapper = pathMapper;
  }

  ToolDefinition getDefinition() {
    return new ToolDefinition(
        "apply_patch",
        "Apply a Unified Diff (patch) to a file. Useful for complex changes that are hard to express as simple replacements.",
        """
            {
              "type": "object",
              "properties": {
                "file_path": { "type": "string", "description": "Path to the file to patch" },
                "patch": { "type": "string", "description": "The Unified Diff content to apply" }
              },
              "required": ["file_path", "patch"]
            }
            """,
        false);
  }

  Future<ToolInvokeResult> execute(Map<String, Object> args) {
    String rawPath = (String) args.get("file_path");
    String patch = (String) args.get("patch");
    String filePath = pathMapper.map(rawPath);

    if (filePath == null || patch == null) {
      return Future.succeededFuture(
          ToolInvokeResult.error("Missing required arguments for apply_patch."));
    }

    return fileSystem
        .exists(filePath)
        .compose(exists -> validateFileExists(exists, filePath))
        .compose(v -> performPatch(filePath, rawPath, patch));
  }

  private Future<Void> validateFileExists(boolean exists, String filePath) {
    if (!exists) {
      return Future.failedFuture(new SecurityException("File not found: " + filePath));
    }
    return Future.succeededFuture();
  }

  private Future<ToolInvokeResult> performPatch(String filePath, String rawPath, String patch) {
    String patchPath = filePath + ".patch." + System.nanoTime();
    Buffer patchBuffer = Buffer.buffer(patch);

    return fileSystem
        .writeFile(patchPath, patchBuffer)
        .compose(v -> executePatchCommand(filePath, patchPath))
        .compose(
            result ->
                fileSystem
                    .delete(patchPath)
                    .map(
                        v -> {
                          if (result.exitCode() == 0) {
                            return ToolInvokeResult.success(
                                "SUCCESS: Applied patch to " + rawPath + "\n" + result.output());
                          }
                          return ToolInvokeResult.error(
                              "PATCH_FAILURE: Exit code "
                                  + result.exitCode()
                                  + ". "
                                  + result.output());
                        }))
        .recover(
            err ->
                fileSystem
                    .delete(patchPath)
                    .compose(v -> Future.<ToolInvokeResult>failedFuture(err))
                    .recover(e2 -> Future.failedFuture(err)));
  }

  private Future<VertxProcess.Result> executePatchCommand(String filePath, String patchPath) {
    List<String> command = List.of("patch", "-u", filePath, "-i", patchPath);
    return VertxProcess.execute(vertx, command, 30000, 1024 * 1024);
  }
}
