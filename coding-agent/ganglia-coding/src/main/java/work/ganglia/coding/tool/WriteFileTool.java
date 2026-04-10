package work.ganglia.coding.tool;

import java.io.File;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.CopyOptions;
import io.vertx.core.file.FileSystem;

import work.ganglia.coding.tool.util.DiffGenerator;
import work.ganglia.port.external.tool.ToolDefinition;
import work.ganglia.port.external.tool.model.ToolInvokeResult;
import work.ganglia.util.PathMapper;

/** Write (or overwrite) complete file content with diff generation. */
class WriteFileTool {
  private static final Logger logger = LoggerFactory.getLogger(WriteFileTool.class);

  private final FileSystem fileSystem;
  private final PathMapper pathMapper;
  private final DiffGenerator diffGenerator;

  WriteFileTool(FileSystem fileSystem, PathMapper pathMapper, DiffGenerator diffGenerator) {
    this.fileSystem = fileSystem;
    this.pathMapper = pathMapper;
    this.diffGenerator = diffGenerator;
  }

  ToolDefinition getDefinition() {
    return new ToolDefinition(
        "write_file",
        "Write (or overwrite) the complete content of a file. Follows Write-then-Move pattern for safety.",
        """
            {
              "type": "object",
              "properties": {
                "file_path": { "type": "string", "description": "Path to the file to write" },
                "content": { "type": "string", "description": "The complete literal content to write to the file" }
              },
              "required": ["file_path", "content"]
            }
            """,
        false);
  }

  Future<ToolInvokeResult> execute(Map<String, Object> args) {
    String rawPath = (String) args.get("file_path");
    String content = (String) args.get("content");
    String filePath = pathMapper.map(rawPath);

    if (filePath == null || content == null) {
      return Future.succeededFuture(
          ToolInvokeResult.error("Missing required arguments for write_file."));
    }

    return fileSystem
        .exists(filePath)
        .compose(exists -> exists ? fileSystem.readFile(filePath) : Future.succeededFuture(null))
        .compose(oldBuffer -> performWriteWithDiff(filePath, rawPath, content, oldBuffer))
        .map(diff -> ToolInvokeResult.success("SUCCESS: Wrote file " + rawPath, diff));
  }

  private Future<String> performWriteWithDiff(
      String filePath, String rawPath, String content, Buffer oldBuffer) {
    String tempOldPath = filePath + ".old." + System.nanoTime();
    String tempPath = filePath + ".tmp." + System.nanoTime();
    Buffer newBuffer = Buffer.buffer(content);

    Future<Void> prepareOld =
        (oldBuffer != null)
            ? fileSystem.writeFile(tempOldPath, oldBuffer)
            : Future.succeededFuture();

    return prepareOld
        .compose(v -> fileSystem.writeFile(tempPath, newBuffer))
        .compose(
            v -> {
              String oldFileForDiff = (oldBuffer != null) ? tempOldPath : "/dev/null";
              return diffGenerator.generateDiff(oldFileForDiff, content, rawPath);
            })
        .compose(diff -> finalizeWrite(filePath, tempPath, tempOldPath, oldBuffer != null, diff))
        .recover(
            err -> {
              logger.error("Failed to write file: {}", filePath, err);
              if (oldBuffer != null) {
                return fileSystem
                    .delete(tempOldPath)
                    .compose(v -> Future.<String>failedFuture(err))
                    .recover(e2 -> Future.failedFuture(err));
              }
              return Future.failedFuture(err);
            });
  }

  private Future<String> finalizeWrite(
      String filePath, String tempPath, String tempOldPath, boolean hasOld, String diff) {
    File parent = new File(filePath).getParentFile();
    Future<Void> mkdirsFuture =
        (parent != null) ? fileSystem.mkdirs(parent.getAbsolutePath()) : Future.succeededFuture();

    return mkdirsFuture
        .compose(
            v -> fileSystem.move(tempPath, filePath, new CopyOptions().setReplaceExisting(true)))
        .compose(v -> hasOld ? fileSystem.delete(tempOldPath) : Future.succeededFuture())
        .map(v -> diff);
  }
}
