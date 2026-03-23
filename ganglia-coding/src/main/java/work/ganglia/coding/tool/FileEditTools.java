package work.ganglia.coding.tool;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.CopyOptions;
import io.vertx.core.file.FileSystem;
import java.io.File;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.ganglia.infrastructure.external.tool.model.ToolInvokeResult;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.external.tool.ToolDefinition;
import work.ganglia.port.external.tool.ToolSet;
import work.ganglia.util.PathMapper;
import work.ganglia.util.PathSanitizer;
import work.ganglia.util.VertxProcess;

/** Tools for surgical file editing (precise replacement) and full file modification. */
public class FileEditTools implements ToolSet {
  private static final Logger logger = LoggerFactory.getLogger(FileEditTools.class);

  private final Vertx vertx;
  private final FileSystem fileSystem;
  private final PathMapper pathMapper;

  public FileEditTools(Vertx vertx) {
    this(vertx, new PathSanitizer());
  }

  public FileEditTools(Vertx vertx, PathMapper pathMapper) {
    this.vertx = vertx;
    this.fileSystem = vertx.fileSystem();
    this.pathMapper = pathMapper;
  }

  @Override
  public List<ToolDefinition> getDefinitions() {
    return List.of(
        new ToolDefinition(
            "replace_in_file",
            "Precisely replace a code block in a file. Requires exact matching. "
                + "Include at least 3 lines of context BEFORE and AFTER the target text to ensure a unique match.",
            """
                {
                  "type": "object",
                  "properties": {
                    "file_path": {
                      "type": "string",
                      "description": "Path to the file to modify"
                    },
                    "old_string": {
                      "type": "string",
                      "description": "The exact literal text to replace, including all whitespace, indentation, and context lines."
                    },
                    "new_string": {
                      "type": "string",
                      "description": "The new text to replace 'old_string' with."
                    },
                    "expected_replacements": {
                      "type": "integer",
                      "description": "Number of replacements expected. Defaults to 1. If the count in the file doesn't match this, the operation fails.",
                      "default": 1
                    }
                  },
                  "required": ["file_path", "old_string", "new_string"]
                }
                """,
            false),
        new ToolDefinition(
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
            false),
        new ToolDefinition(
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
            false));
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
            case "replace_in_file" -> replaceInFile(toolName, args);
            case "write_file" -> writeFile(args);
            case "apply_patch" -> applyPatch(args);
            default -> Future.failedFuture("Unknown tool: " + toolName);
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

  private Future<ToolInvokeResult> replaceInFile(String toolName, Map<String, Object> args) {
    String rawPath = (String) args.get("file_path");
    String filePath = pathMapper.map(rawPath);
    String oldString = (String) args.get("old_string");
    String newString = (String) args.get("new_string");
    int expected = parseExpectedReplacements(args.getOrDefault("expected_replacements", 1));

    if (filePath == null || oldString == null || newString == null) {
      return Future.succeededFuture(
          ToolInvokeResult.error("Missing required arguments for replace_in_file."));
    }

    logger.debug("Attempting surgical replacement in {}. Expected: {}", filePath, expected);

    return fileSystem
        .exists(filePath)
        .compose(exists -> validateFileExists(exists, filePath))
        .compose(v -> fileSystem.readFile(filePath))
        .compose(buffer -> performReplacement(buffer, oldString, newString, expected, filePath))
        .compose(
            replacement ->
                saveWithDiff(filePath, rawPath, replacement.oldBuffer(), replacement.newContent())
                    .map(
                        diff ->
                            ToolInvokeResult.success(
                                "SUCCESS: Replaced "
                                    + replacement.count()
                                    + " occurrence(s) in "
                                    + rawPath,
                                diff)));
  }

  private int parseExpectedReplacements(Object expectedObj) {
    if (expectedObj instanceof Number) {
      return ((Number) expectedObj).intValue();
    }
    if (expectedObj instanceof String str) {
      try {
        return Integer.parseInt(str);
      } catch (NumberFormatException e) {
        logger.warn("Invalid expected_replacements value: '{}', defaulting to 1", str);
        return 1;
      }
    }
    return 1;
  }

  private Future<Void> validateFileExists(boolean exists, String filePath) {
    if (!exists) {
      return Future.failedFuture(new SecurityException("File not found: " + filePath));
    }
    return Future.succeededFuture();
  }

  private record ReplacementResult(Buffer oldBuffer, String newContent, int count) {}

  private Future<ReplacementResult> performReplacement(
      Buffer buffer, String oldString, String newString, int expected, String filePath) {
    String content = buffer.toString();
    int actualCount = countOccurrences(content, oldString);

    if (actualCount == 0) {
      return Future.failedFuture(
          new SecurityException(
              "MATCH_FAILURE: Could not find the exact 'old_string' in "
                  + filePath
                  + ". Ensure whitespace, indentation, and newlines match exactly."));
    }

    if (actualCount != expected) {
      return Future.failedFuture(
          new SecurityException(
              "AMBIGUITY_FAILURE: Expected "
                  + expected
                  + " occurrence(s), but found "
                  + actualCount
                  + " in "
                  + filePath
                  + ". Please provide more context to identify a unique match."));
    }

    String updatedContent = content.replace(oldString, newString);
    return Future.succeededFuture(new ReplacementResult(buffer, updatedContent, actualCount));
  }

  private Future<String> saveWithDiff(
      String filePath, String rawPath, Buffer oldBuffer, String newContent) {
    String tempOldPath = filePath + ".old." + System.nanoTime();
    String tempPath = filePath + ".tmp." + System.nanoTime();

    return fileSystem
        .writeFile(tempOldPath, oldBuffer)
        .compose(v -> generateDiff(tempOldPath, newContent, rawPath))
        .compose(
            diff ->
                fileSystem
                    .writeFile(tempPath, Buffer.buffer(newContent))
                    .compose(
                        v ->
                            fileSystem.move(
                                tempPath, filePath, new CopyOptions().setReplaceExisting(true)))
                    .compose(v -> fileSystem.delete(tempOldPath))
                    .map(v -> diff))
        .recover(
            err -> {
              logger.error("Failed to save file changes: {}", filePath, err);
              return fileSystem
                  .delete(tempOldPath)
                  .compose(v -> Future.<String>failedFuture(err))
                  .recover(e2 -> Future.failedFuture(err));
            });
  }

  private Future<ToolInvokeResult> writeFile(Map<String, Object> args) {
    String rawPath = (String) args.get("file_path");
    String content = (String) args.get("content");
    String filePath = pathMapper.map(rawPath);

    if (filePath == null || content == null) {
      return Future.succeededFuture(
          ToolInvokeResult.error("Missing required arguments for write_file."));
    }

    return fileSystem
        .exists(filePath)
        .compose(exists -> readOldContentIfAvailable(exists, filePath))
        .compose(oldBuffer -> performWriteWithDiff(filePath, rawPath, content, oldBuffer))
        .map(diff -> ToolInvokeResult.success("SUCCESS: Wrote file " + rawPath, diff));
  }

  private Future<Buffer> readOldContentIfAvailable(boolean exists, String filePath) {
    return exists ? fileSystem.readFile(filePath) : Future.succeededFuture(null);
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
              String tempNewPathForDiff = filePath + ".new." + System.nanoTime();
              return generateDiffInternal(oldFileForDiff, content, rawPath, tempNewPathForDiff);
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

  private Future<ToolInvokeResult> applyPatch(Map<String, Object> args) {
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

  private Future<ToolInvokeResult> performPatch(String filePath, String rawPath, String patch) {
    String patchPath = filePath + ".patch." + System.nanoTime();
    Buffer patchBuffer = Buffer.buffer(patch);

    return fileSystem
        .writeFile(patchPath, patchBuffer)
        .compose(v -> executePatchCommand(filePath, patchPath))
        .compose(result -> cleanupAndReturnPatchResult(patchPath, result, rawPath))
        .recover(err -> cleanupAndFailPatch(patchPath, err));
  }

  private Future<VertxProcess.Result> executePatchCommand(String filePath, String patchPath) {
    List<String> command = List.of("patch", "-u", filePath, "-i", patchPath);
    return VertxProcess.execute(vertx, command, 30000, 1024 * 1024);
  }

  private Future<ToolInvokeResult> cleanupAndReturnPatchResult(
      String patchPath, VertxProcess.Result result, String rawPath) {
    return fileSystem
        .delete(patchPath)
        .map(
            v -> {
              if (result.exitCode() == 0) {
                return ToolInvokeResult.success(
                    "SUCCESS: Applied patch to " + rawPath + "\n" + result.output());
              }
              return ToolInvokeResult.error(
                  "PATCH_FAILURE: Exit code " + result.exitCode() + ". " + result.output());
            });
  }

  private Future<ToolInvokeResult> cleanupAndFailPatch(String patchPath, Throwable err) {
    return fileSystem
        .delete(patchPath)
        .compose(v -> Future.<ToolInvokeResult>failedFuture(err))
        .recover(e2 -> Future.failedFuture(err));
  }

  private Future<String> generateDiff(String oldFilePath, String newContent, String label) {
    String tempNewPath = oldFilePath + ".new." + System.nanoTime();
    return generateDiffInternal(oldFilePath, newContent, label, tempNewPath);
  }

  private Future<String> generateDiffInternal(
      String oldFilePath, String newContent, String label, String tempNewPath) {
    return fileSystem
        .writeFile(tempNewPath, Buffer.buffer(newContent))
        .compose(v -> executeDiffCommand(oldFilePath, tempNewPath))
        .compose(result -> cleanupAndReturnDiffResult(tempNewPath, result, label))
        .recover(err -> cleanupAndFailDiff(tempNewPath, err));
  }

  private Future<VertxProcess.Result> executeDiffCommand(String oldFilePath, String tempNewPath) {
    var command = String.format("diff -u %s %s", oldFilePath, tempNewPath);
    return VertxProcess.execute(vertx, List.of("bash", "-c", command), 10000, 1024 * 1024);
  }

  private Future<String> cleanupAndReturnDiffResult(
      String tempNewPath, VertxProcess.Result result, String label) {
    return fileSystem
        .delete(tempNewPath)
        .map(
            v -> {
              String output = result.output();
              if (output.startsWith("---")) {
                String[] lines = output.split("\\n", 3);
                if (lines.length >= 2) {
                  return "--- "
                      + label
                      + "\n"
                      + "+++ "
                      + label
                      + "\n"
                      + (lines.length > 2 ? lines[2] : "");
                }
              }
              return output;
            });
  }

  private Future<String> cleanupAndFailDiff(String tempNewPath, Throwable err) {
    logger.warn("Failed to generate diff: {}", err.getMessage());
    return fileSystem
        .delete(tempNewPath)
        .map(v -> "Diff generation failed: " + err.getMessage())
        .recover(e2 -> Future.succeededFuture("Diff generation failed: " + err.getMessage()));
  }

  private int countOccurrences(String text, String target) {
    if (target.isEmpty()) {
      return 0;
    }
    int count = 0;
    int index = 0;
    while ((index = text.indexOf(target, index)) != -1) {
      count++;
      index += target.length();
    }
    return count;
  }
}
