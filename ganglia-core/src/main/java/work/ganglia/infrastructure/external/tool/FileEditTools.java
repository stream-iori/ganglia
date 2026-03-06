package work.ganglia.infrastructure.external.tool;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.file.CopyOptions;
import io.vertx.core.file.FileSystem;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.external.tool.ToolDefinition;
import work.ganglia.infrastructure.external.tool.model.ToolInvokeResult;
import work.ganglia.port.chat.SessionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.ganglia.port.external.tool.ToolSet;

import java.util.List;
import java.util.Map;

/**
 * Tools for surgical file editing (precise replacement).
 */
public class FileEditTools implements ToolSet {
    private static final Logger logger = LoggerFactory.getLogger(FileEditTools.class);

    private final Vertx vertx;
    private final FileSystem fs;

    public FileEditTools(Vertx vertx) {
        this.vertx = vertx;
        this.fs = vertx.fileSystem();
    }

    @Override
    public List<ToolDefinition> getDefinitions() {
        return List.of(
            new ToolDefinition(
                "replace_in_file",
                "Precisely replace a code block in a file. Requires exact matching. " +
                "Include at least 3 lines of context BEFORE and AFTER the target text to ensure a unique match.",
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
                false
            )
        );
    }

    @Override
    public Future<ToolInvokeResult> execute(ToolCall call, SessionContext context) {
        return execute(call.toolName(), call.arguments(), context);
    }

    @Override
    public Future<ToolInvokeResult> execute(String toolName, Map<String, Object> args, SessionContext context) {
        if ("replace_in_file".equals(toolName)) {
            return replaceInFile(toolName, args);
        }
        return Future.failedFuture("Unknown tool: " + toolName);
    }

    private Future<ToolInvokeResult> replaceInFile(String toolName, Map<String, Object> args) {
        String filePath = (String) args.get("file_path");
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
            return Future.succeededFuture(ToolInvokeResult.error("Missing required arguments for replace_in_file."));
        }

        logger.debug("Attempting surgical replacement in {}. Expected: {}", filePath, expected);

        return fs.exists(filePath)
            .compose(exists -> {
                if (!exists) {
                    return Future.succeededFuture(ToolInvokeResult.error("File not found: " + filePath));
                }

                return fs.readFile(filePath)
                    .compose(buffer -> {
                        String content = buffer.toString();

                        // 1. Strict literal matching count
                        int actualCount = countOccurrences(content, oldString);

                        if (actualCount == 0) {
                            return Future.succeededFuture(ToolInvokeResult.error(
                                "MATCH_FAILURE: Could not find the exact 'old_string' in " + filePath + ". " +
                                "Ensure whitespace, indentation, and newlines match exactly."
                            ));
                        }

                        if (actualCount != expected) {
                            return Future.succeededFuture(ToolInvokeResult.error(
                                "AMBIGUITY_FAILURE: Expected " + expected + " occurrence(s), but found " + actualCount +
                                " in " + filePath + ". Please provide more context to identify a unique match."
                            ));
                        }

                        // 2. Perform replacement
                        String updatedContent = content.replace(oldString, newString);

                        // 3. Generate Diff (using a temporary file)
                        String tempOldPath = filePath + ".old." + System.nanoTime();

                        return fs.writeFile(tempOldPath, buffer) // Save current content to temp
                            .compose(v -> generateDiff(tempOldPath, updatedContent, filePath))
                            .compose(diff -> {
                                // 4. Atomic write (temporary file pattern)
                                String tempPath = filePath + ".tmp." + System.nanoTime();

                                return fs.writeFile(tempPath, io.vertx.core.buffer.Buffer.buffer(updatedContent))
                                    .compose(v -> fs.move(tempPath, filePath, new CopyOptions().setReplaceExisting(true)))
                                    .compose(v -> fs.delete(tempOldPath)) // Clean up temp old file
                                    .map(v -> ToolInvokeResult.success("SUCCESS: Replaced " + actualCount + " occurrence(s) in " + filePath, diff))
                                    .recover(err -> {
                                        logger.error("Failed to write updated file: {}", filePath, err);
                                        return fs.delete(tempOldPath)
                                            .compose(v2 -> Future.succeededFuture(ToolInvokeResult.error("FS_ERROR: Failed to save changes: " + err.getMessage())));
                                    });
                            });
                    });
            });
    }

    private Future<String> generateDiff(String oldFilePath, String newContent, String label) {
        String tempNewPath = oldFilePath + ".new";
        return fs.writeFile(tempNewPath, io.vertx.core.buffer.Buffer.buffer(newContent))
            .compose(v -> {
                // Use native diff command for simplicity and reliability on Unix/MacOS
                String command = String.format("diff -u %s %s", oldFilePath, tempNewPath);
                return vertx.executeBlocking(() -> {
                    try {
                        Process process = new ProcessBuilder("bash", "-c", command).start();
                        String output = new String(process.getInputStream().readAllBytes());
                        process.waitFor();
                        // diff returns 1 if differences are found, which is what we expect

                        // Clean up temp new file
                        fs.deleteBlocking(tempNewPath);

                        // Basic cleanup of the diff header to use the actual filename
                        if (output.startsWith("---")) {
                            String[] lines = output.split("\\n", 3);
                            if (lines.length >= 2) {
                                return "--- " + label + "\n" + "+++ " + label + "\n" + (lines.length > 2 ? lines[2] : "");
                            }
                        }
                        return output;
                    } catch (Exception e) {
                        logger.warn("Failed to generate diff: {}", e.getMessage());
                        fs.deleteBlocking(tempNewPath);
                        return "Diff generation failed: " + e.getMessage();
                    }
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
