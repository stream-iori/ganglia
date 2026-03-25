package work.ganglia.coding.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.parsetools.RecordParser;

import work.ganglia.infrastructure.external.tool.model.ToolInvokeResult;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ToolDefinition;
import work.ganglia.port.external.tool.ToolSet;
import work.ganglia.util.PathMapper;
import work.ganglia.util.PathSanitizer;

/** Built-in tools for local filesystem operations using native Vert.x FileSystem API. */
public class NativeFileSystemTools implements ToolSet {
  private static final Logger logger = LoggerFactory.getLogger(NativeFileSystemTools.class);

  static final int DEFAULT_PAGE_SIZE = 500;
  static final int MAX_LINE_LENGTH = 2000;
  static final int DEFAULT_LIMIT_PER_FILE = 300;

  private final Vertx vertx;
  private final FileSystem fileSystem;
  private final PathMapper pathMapper;

  public NativeFileSystemTools(Vertx vertx) {
    this(vertx, new PathSanitizer());
  }

  public NativeFileSystemTools(Vertx vertx, PathMapper pathMapper) {
    this.vertx = vertx;
    this.fileSystem = vertx.fileSystem();
    this.pathMapper = pathMapper;
  }

  @Override
  public List<ToolDefinition> getDefinitions() {
    return List.of(
        new ToolDefinition(
            "list_directory",
            "List files in a directory",
            """
                {
                  "type": "object",
                  "properties": {
                    "path": {
                      "type": "string",
                      "description": "The directory path to list"
                    }
                  },
                  "required": ["path"]
                }
                """),
        new ToolDefinition(
            "read_file",
            "Read content of a file with line-based pagination (1-based)",
            """
                {
                  "type": "object",
                  "properties": {
                    "path": { "type": "string", "description": "The file path to read" },
                    "start_line": { "type": "integer", "description": "1-based line index to start reading from. Defaults to 1.", "default": 1 },
                    "end_line": { "type": "integer", "description": "1-based line index to stop reading at (inclusive). If not provided, reads up to 500 lines." }
                  },
                  "required": ["path"]
                }
                """),
        new ToolDefinition(
            "read_files",
            "Read multiple files at once",
            """
                {
                  "type": "object",
                  "properties": {
                    "paths": {
                      "type": "array",
                      "items": { "type": "string" },
                      "description": "List of file paths to read"
                    },
                    "limit_per_file": {
                      "type": "integer",
                      "description": "Maximum number of lines to read per file. Defaults to 300.",
                      "default": 300
                    }
                  },
                  "required": ["paths"]
                }
                """));
  }

  @Override
  public Future<ToolInvokeResult> execute(
      String toolName,
      Map<String, Object> args,
      SessionContext context,
      work.ganglia.port.internal.state.ExecutionContext executionContext) {
    try {
      return switch (toolName) {
        case "list_directory" -> listDirectory(args);
        case "read_file" -> readFile(args);
        case "read_files" -> readFiles(args);
        default -> Future.succeededFuture(ToolInvokeResult.error("Unknown tool: " + toolName));
      };
    } catch (SecurityException | IllegalArgumentException e) {
      logger.warn("[SANDBOX_VIOLATION] Tool: {}, Error: {}", toolName, e.getMessage());
      return Future.succeededFuture(
          ToolInvokeResult.error("Security/Validation Error: " + e.getMessage()));
    }
  }

  private Future<ToolInvokeResult> listDirectory(Map<String, Object> args) {
    String rawPath = (String) args.get("path");
    String safePath = pathMapper.map(rawPath);

    return validateDirectory(safePath)
        .compose(v -> fileSystem.readDir(safePath))
        .compose(this::formatDirectoryEntries)
        .recover(
            err ->
                Future.succeededFuture(
                    ToolInvokeResult.error("Error listing directory: " + err.getMessage())));
  }

  private Future<Void> validateDirectory(String path) {
    return fileSystem
        .exists(path)
        .compose(
            exists -> {
              if (!exists) {
                return Future.failedFuture("Directory not found: " + path);
              }
              return fileSystem.props(path);
            })
        .compose(
            props -> {
              if (!props.isDirectory()) {
                return Future.failedFuture("Path is not a directory: " + path);
              }
              return Future.succeededFuture();
            });
  }

  private Future<ToolInvokeResult> formatDirectoryEntries(List<String> files) {
    List<Future<String>> formattedFiles = new ArrayList<>();
    for (String file : files) {
      formattedFiles.add(
          fileSystem
              .props(file)
              .map(
                  fileProps -> {
                    String name = new java.io.File(file).getName();
                    if (fileProps.isDirectory()) {
                      return name + "/";
                    } else if (fileProps.isSymbolicLink()) {
                      return name + "@";
                    } else {
                      return name;
                    }
                  })
              .recover(
                  err -> Future.succeededFuture(new java.io.File(file).getName() + " [error]")));
    }

    return Future.all(formattedFiles)
        .map(
            cf -> {
              List<String> list = cf.list();
              list.sort(String::compareToIgnoreCase);
              return ToolInvokeResult.success(String.join("\n", list));
            });
  }

  Future<ToolInvokeResult> readFile(Map<String, Object> args) {
    String rawPath = (String) args.get("path");
    String safePath = pathMapper.map(rawPath);

    int startLine = ((Number) args.getOrDefault("start_line", 1)).intValue();
    int endLine =
        args.containsKey("end_line")
            ? ((Number) args.get("end_line")).intValue()
            : startLine + DEFAULT_PAGE_SIZE - 1;

    if (startLine < 1) {
      startLine = 1;
    }
    if (endLine < startLine) {
      return Future.succeededFuture(
          ToolInvokeResult.error("end_line must be greater than or equal to start_line"));
    }

    return readFileLinesAsync(safePath, startLine, endLine)
        .map(ToolInvokeResult::success)
        .recover(
            err ->
                Future.succeededFuture(
                    ToolInvokeResult.error("Error reading file: " + err.getMessage())));
  }

  private Future<String> readFileLinesAsync(String filePath, int startLine, int endLine) {
    return validateFile(filePath)
        .compose(
            v -> {
              Promise<String> promise = Promise.promise();
              fileSystem
                  .open(filePath, new OpenOptions().setRead(true).setWrite(false))
                  .onSuccess(asyncFile -> setupRecordParser(asyncFile, startLine, endLine, promise))
                  .onFailure(promise::fail);
              return promise.future();
            });
  }

  private Future<Void> validateFile(String filePath) {
    return fileSystem
        .exists(filePath)
        .compose(
            exists -> {
              if (!exists) {
                return Future.failedFuture("File not found: " + filePath);
              }
              return fileSystem.props(filePath);
            })
        .compose(
            props -> {
              if (props.isDirectory()) {
                return Future.failedFuture("Path is a directory, not a file: " + filePath);
              }
              return Future.succeededFuture();
            });
  }

  private void setupRecordParser(
      AsyncFile asyncFile, int startLine, int endLine, Promise<String> promise) {
    AtomicInteger currentLine = new AtomicInteger(1);
    StringBuilder content = new StringBuilder();

    RecordParser parser = RecordParser.newDelimited("\n", asyncFile);

    parser.handler(
        buffer -> {
          int lineNo = currentLine.getAndIncrement();

          if (lineNo < startLine) {
            return;
          }

          if (lineNo > endLine) {
            parser.pause();
            if (promise.tryComplete(buildResult(content, startLine, endLine, true))) {
              asyncFile.close().recover(e -> Future.succeededFuture());
            }
            return;
          }

          String line = buffer.toString("UTF-8");
          if (line.length() > MAX_LINE_LENGTH) {
            content.append(line, 0, MAX_LINE_LENGTH).append(" [...Line truncated...]\n");
          } else {
            content.append(line).append("\n");
          }
        });

    parser.endHandler(
        v -> {
          if (promise.tryComplete(buildResult(content, startLine, currentLine.get() - 1, false))) {
            asyncFile.close().recover(e -> Future.succeededFuture());
          }
        });

    parser.exceptionHandler(
        err -> {
          if (promise.tryFail(err)) {
            asyncFile.close().recover(e -> Future.succeededFuture());
          }
        });
  }

  private String buildResult(StringBuilder content, int start, int end, boolean hasMore) {
    if (end < start) {
      end = start;
    }
    content
        .append("\n--- [Lines ")
        .append(start)
        .append(" to ")
        .append(end)
        .append(" shown] ---\n");
    if (hasMore) {
      content
          .append("Hint: More lines available. Use 'read_file' with 'start_line: ")
          .append(end + 1)
          .append("' to read more.\n");
    }
    return content.toString();
  }

  @SuppressWarnings("unchecked")
  private Future<ToolInvokeResult> readFiles(Map<String, Object> args) {
    List<String> paths = (List<String>) args.get("paths");
    if (paths == null || paths.isEmpty()) {
      return Future.succeededFuture(ToolInvokeResult.error("No paths provided"));
    }

    int limitPerFile =
        ((Number) args.getOrDefault("limit_per_file", DEFAULT_LIMIT_PER_FILE)).intValue();

    List<Future<String>> readFutures =
        paths.stream()
            .map(
                rawPath -> {
                  String safePath = pathMapper.map(rawPath);
                  return readFileLinesAsync(safePath, 1, limitPerFile)
                      .map(content -> "--- FILE: " + safePath + " ---\n" + content + "\n")
                      .recover(
                          err ->
                              Future.succeededFuture(
                                  "--- FILE: "
                                      + safePath
                                      + " ---\n[ERROR: "
                                      + err.getMessage()
                                      + "]\n\n"));
                })
            .collect(Collectors.toList());

    return Future.all(readFutures)
        .map(
            cf -> {
              List<String> results = cf.list();
              return ToolInvokeResult.success(String.join("", results));
            });
  }
}
