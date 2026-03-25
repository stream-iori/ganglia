package work.ganglia.coding.tool;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.CopyOptions;
import io.vertx.core.file.FileSystem;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.ganglia.coding.tool.util.DiffGenerator;
import work.ganglia.infrastructure.external.tool.model.ToolInvokeResult;
import work.ganglia.port.external.tool.ToolDefinition;
import work.ganglia.util.PathMapper;

/** Precise string replacement in files with diff generation. */
class ReplaceInFileTool {
  private static final Logger logger = LoggerFactory.getLogger(ReplaceInFileTool.class);

  private final FileSystem fileSystem;
  private final PathMapper pathMapper;
  private final DiffGenerator diffGenerator;

  ReplaceInFileTool(FileSystem fileSystem, PathMapper pathMapper, DiffGenerator diffGenerator) {
    this.fileSystem = fileSystem;
    this.pathMapper = pathMapper;
    this.diffGenerator = diffGenerator;
  }

  ToolDefinition getDefinition() {
    return new ToolDefinition(
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
        false);
  }

  Future<ToolInvokeResult> execute(Map<String, Object> args) {
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
        .compose(exists -> FileEditTools.validateFileExists(exists, filePath))
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
        .compose(v -> diffGenerator.generateDiff(tempOldPath, newContent, rawPath))
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
