package work.ganglia.coding.tool;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
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
import work.ganglia.util.PathSanitizer;
import work.ganglia.util.VertxProcess;

/** Tools for surgical file editing (precise replacement) and full file modification. */
public class FileEditTools implements ToolSet {
  private static final Logger logger = LoggerFactory.getLogger(FileEditTools.class);

  private final Vertx vertx;
  private final FileSystem fs;
  private final PathSanitizer sanitizer;

  public FileEditTools(Vertx vertx) {
    this(vertx, new PathSanitizer());
  }

  public FileEditTools(Vertx vertx, PathSanitizer sanitizer) {
    this.vertx = vertx;
    this.fs = vertx.fileSystem();
    this.sanitizer = sanitizer;
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
    try {
      return switch (toolName) {
        case "replace_in_file" -> replaceInFile(toolName, args);
        case "write_file" -> writeFile(args);
        case "apply_patch" -> applyPatch(args);
        default -> Future.failedFuture("Unknown tool: " + toolName);
      };
    } catch (SecurityException e) {
      return Future.succeededFuture(
          ToolInvokeResult.error("Security/Validation Error: " + e.getMessage()));
    }
  }

  private Future<ToolInvokeResult> replaceInFile(String toolName, Map<String, Object> args) {
    String rawPath = (String) args.get("file_path");
    String filePath = sanitizer.sanitize(rawPath);
    String oldString = (String) args.get("old_string");
    String newString = (String) args.get("new_string");

    Object expectedObj = args.getOrDefault("expected_replacements", 1);
    int expected;
    if (expectedObj instanceof Number) {
      expected = ((Number) expectedObj).intValue();
    } else if (expectedObj instanceof String) {
      expected = Integer.parseInt((String) expectedObj);
    } else {
      expected = 1;
    }

    if (filePath == null || oldString == null || newString == null) {
      return Future.succeededFuture(
          ToolInvokeResult.error("Missing required arguments for replace_in_file."));
    }

    logger.debug("Attempting surgical replacement in {}. Expected: {}", filePath, expected);

    return fs.exists(filePath)
        .compose(
            exists -> {
              if (!exists) {
                return Future.succeededFuture(
                    ToolInvokeResult.error("File not found: " + filePath));
              }

              return fs.readFile(filePath)
                  .compose(
                      buffer -> {
                        String content = buffer.toString();

                        // 1. Strict literal matching count
                        int actualCount = countOccurrences(content, oldString);

                        if (actualCount == 0) {
                          return Future.succeededFuture(
                              ToolInvokeResult.error(
                                  "MATCH_FAILURE: Could not find the exact 'old_string' in "
                                      + filePath
                                      + ". "
                                      + "Ensure whitespace, indentation, and newlines match exactly."));
                        }

                        if (actualCount != expected) {
                          return Future.succeededFuture(
                              ToolInvokeResult.error(
                                  "AMBIGUITY_FAILURE: Expected "
                                      + expected
                                      + " occurrence(s), but found "
                                      + actualCount
                                      + " in "
                                      + filePath
                                      + ". Please provide more context to identify a unique match."));
                        }

                        // 2. Perform replacement
                        String updatedContent = content.replace(oldString, newString);

                        // 3. Generate Diff (using a temporary file)
                        String tempOldPath = filePath + ".old." + System.nanoTime();

                        return fs.writeFile(tempOldPath, buffer) // Save current content to temp
                            .compose(v -> generateDiff(tempOldPath, updatedContent, rawPath))
                            .compose(
                                diff -> {
                                  // 4. Atomic write (temporary file pattern)
                                  String tempPath = filePath + ".tmp." + System.nanoTime();

                                  return fs.writeFile(
                                          tempPath,
                                          io.vertx.core.buffer.Buffer.buffer(updatedContent))
                                      .compose(
                                          v ->
                                              fs.move(
                                                  tempPath,
                                                  filePath,
                                                  new CopyOptions().setReplaceExisting(true)))
                                      .compose(
                                          v -> fs.delete(tempOldPath)) // Clean up temp old file
                                      .map(
                                          v ->
                                              ToolInvokeResult.success(
                                                  "SUCCESS: Replaced "
                                                      + actualCount
                                                      + " occurrence(s) in "
                                                      + rawPath,
                                                  diff))
                                      .recover(
                                          err -> {
                                            logger.error(
                                                "Failed to write updated file: {}", filePath, err);
                                            return fs.delete(tempOldPath)
                                                .compose(
                                                    v2 ->
                                                        Future.succeededFuture(
                                                            ToolInvokeResult.error(
                                                                "FS_ERROR: Failed to save changes: "
                                                                    + err.getMessage())));
                                          });
                                });
                      });
            });
  }

  private Future<ToolInvokeResult> writeFile(Map<String, Object> args) {
    String rawPath = (String) args.get("file_path");
    String content = (String) args.get("content");
    String filePath = sanitizer.sanitize(rawPath);

    if (filePath == null || content == null) {
      return Future.succeededFuture(
          ToolInvokeResult.error("Missing required arguments for write_file."));
    }

    return fs.exists(filePath)
        .compose(
            exists -> {
              Future<io.vertx.core.buffer.Buffer> oldBufferFuture =
                  exists ? fs.readFile(filePath) : Future.succeededFuture(null);

              return oldBufferFuture.compose(
                  oldBuffer -> {
                    String tempOldPath = filePath + ".old." + System.nanoTime();
                    String tempPath = filePath + ".tmp." + System.nanoTime();

                    Future<Void> prepareOld =
                        (oldBuffer != null)
                            ? fs.writeFile(tempOldPath, oldBuffer)
                            : Future.succeededFuture();

                    return prepareOld
                        .compose(
                            v ->
                                fs.writeFile(tempPath, io.vertx.core.buffer.Buffer.buffer(content)))
                        .compose(
                            v2 -> {
                              String oldFileForDiff =
                                  (oldBuffer != null) ? tempOldPath : "/dev/null";
                              // Ensure we use a valid path for the new content temp file
                              String tempNewPath = filePath + ".new." + System.nanoTime();
                              return generateDiffInternal(
                                  oldFileForDiff, content, rawPath, tempNewPath);
                            })
                        .compose(
                            diff -> {
                              File file = new File(filePath);
                              File parent = file.getParentFile();
                              Future<Void> mkdirsFuture =
                                  (parent != null)
                                      ? fs.mkdirs(parent.getAbsolutePath())
                                      : Future.succeededFuture();

                              return mkdirsFuture
                                  .compose(
                                      v3 ->
                                          fs.move(
                                              tempPath,
                                              filePath,
                                              new CopyOptions().setReplaceExisting(true)))
                                  .compose(
                                      v4 ->
                                          (oldBuffer != null)
                                              ? fs.delete(tempOldPath)
                                              : Future.succeededFuture())
                                  .map(
                                      v5 ->
                                          ToolInvokeResult.success(
                                              "SUCCESS: Wrote file " + rawPath, diff))
                                  .recover(
                                      err -> {
                                        logger.error("Failed to write file: {}", filePath, err);
                                        if (oldBuffer != null) fs.delete(tempOldPath);
                                        return Future.succeededFuture(
                                            ToolInvokeResult.error(
                                                "FS_ERROR: " + err.getMessage()));
                                      });
                            });
                  });
            });
  }

  private Future<ToolInvokeResult> applyPatch(Map<String, Object> args) {
    String rawPath = (String) args.get("file_path");
    String patch = (String) args.get("patch");
    String filePath = sanitizer.sanitize(rawPath);

    if (filePath == null || patch == null) {
      return Future.succeededFuture(
          ToolInvokeResult.error("Missing required arguments for apply_patch."));
    }

    return fs.exists(filePath)
        .compose(
            exists -> {
              if (!exists)
                return Future.succeededFuture(ToolInvokeResult.error("File not found: " + rawPath));

              String patchPath = filePath + ".patch." + System.nanoTime();
              return fs.writeFile(patchPath, io.vertx.core.buffer.Buffer.buffer(patch))
                  .compose(
                      v -> {
                        // patch -u [file] -i [patch_file]
                        List<String> command = List.of("patch", "-u", filePath, "-i", patchPath);
                        return VertxProcess.execute(vertx, command, 30000, 1024 * 1024)
                            .compose(
                                result ->
                                    fs.delete(patchPath)
                                        .map(
                                            v2 -> {
                                              if (result.exitCode() == 0) {
                                                return ToolInvokeResult.success(
                                                    "SUCCESS: Applied patch to "
                                                        + rawPath
                                                        + "\n"
                                                        + result.output());
                                              } else {
                                                return ToolInvokeResult.error(
                                                    "PATCH_FAILURE: Exit code "
                                                        + result.exitCode()
                                                        + ". "
                                                        + result.output());
                                              }
                                            }))
                            .recover(
                                err ->
                                    fs.delete(patchPath)
                                        .compose(
                                            v2 ->
                                                Future.succeededFuture(
                                                    ToolInvokeResult.error(
                                                        "PATCH_ERROR: " + err.getMessage()))));
                      });
            });
  }

  private Future<String> generateDiff(String oldFilePath, String newContent, String label) {
    String tempNewPath = oldFilePath + ".new." + System.nanoTime();
    return generateDiffInternal(oldFilePath, newContent, label, tempNewPath);
  }

  private Future<String> generateDiffInternal(
      String oldFilePath, String newContent, String label, String tempNewPath) {
    return fs.writeFile(tempNewPath, io.vertx.core.buffer.Buffer.buffer(newContent))
        .compose(
            v -> {
              // Use native diff command for simplicity and reliability on Unix/MacOS
              String command = String.format("diff -u %s %s", oldFilePath, tempNewPath);
              return VertxProcess.execute(vertx, List.of("bash", "-c", command), 10000, 1024 * 1024)
                  .compose(
                      result ->
                          fs.delete(tempNewPath)
                              .map(
                                  v2 -> {
                                    String output = result.output();
                                    // Basic cleanup of the diff header to use the actual filename
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
                                  }))
                  .recover(
                      err -> {
                        logger.warn("Failed to generate diff: {}", err.getMessage());
                        return fs.delete(tempNewPath)
                            .map(v2 -> "Diff generation failed: " + err.getMessage());
                      });
            });
  }

  private int countOccurrences(String text, String target) {
    if (target.isEmpty()) return 0;
    int count = 0;
    int index = 0;
    while ((index = text.indexOf(target, index)) != -1) {
      count++;
      index += target.length();
    }
    return count;
  }
}
