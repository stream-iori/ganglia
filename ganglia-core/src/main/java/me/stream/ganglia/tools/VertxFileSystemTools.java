package me.stream.ganglia.tools;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import me.stream.ganglia.tools.model.ToolCall;
import me.stream.ganglia.tools.model.ToolDefinition;
import me.stream.ganglia.tools.model.ToolInvokeResult;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Built-in tools for local filesystem operations using JVM/Vert.x APIs.
 */
public class VertxFileSystemTools implements ToolSet {
    private static final long MAX_FILE_SIZE = 8 * 1024; // 8KB
    private final Vertx vertx;

    public VertxFileSystemTools(Vertx vertx) {
        this.vertx = vertx;
    }

    @Override
    public List<ToolDefinition> getDefinitions() {
        return List.of(
            new ToolDefinition("write_file", "Write content to a file (creates or overwrites)",
                """
                {
                  "type": "object",
                  "properties": {
                    "path": { "type": "string", "description": "The file path to write to" },
                    "content": { "type": "string", "description": "The content to write" }
                  },
                  "required": ["path", "content"]
                }
                """),
            new ToolDefinition("vertx_ls", "List files in a directory using JVM API",
                "{\n  \"type\": \"object\",\n  \"properties\": {\n    \"path\": {\n      \"type\": \"string\",\n      \"description\": \"The directory path to list\"\n    }\n  },\n  \"required\": [\"path\"]\n}"),
            new ToolDefinition("vertx_read", "Read content of a file using JVM API",
                "{\n  \"type\": \"object\",\n  \"properties\": {\n    \"path\": {\n      \"type\": \"string\",\n      \"description\": \"The file path to read\"\n    }\n  },\n  \"required\": [\"path\"]\n}")
        );
    }

    @Override
    public Future<ToolInvokeResult> execute(String toolName, Map<String, Object> args, me.stream.ganglia.core.model.SessionContext context) {
        return switch (toolName) {
            case "vertx_ls" -> ls(args);
            case "vertx_read" -> read(args);
            case "write_file" -> write(args);
            default -> Future.succeededFuture(ToolInvokeResult.error("Unknown tool: " + toolName));
        };
    }

    private Future<ToolInvokeResult> ls(Map<String, Object> args) {
        String path = (String) args.get("path");
        return vertx.fileSystem().readDir(path)
            .map(files -> files.stream()
                .map(f -> {
                    File file = new File(f);
                    return file.isDirectory() ? file.getName() + "/" : file.getName();
                })
                .collect(Collectors.joining("\n")))
            .map(ToolInvokeResult::success)
            .recover(err -> Future.succeededFuture(ToolInvokeResult.error("Error listing directory: " + err.getMessage())));
    }

    public Future<ToolInvokeResult> read(Map<String, Object> args) {
        String path = (String) args.get("path");
        return vertx.fileSystem().props(path)
            .compose(props -> {
                if (props.size() > MAX_FILE_SIZE) {
                    return Future.succeededFuture(ToolInvokeResult.error(
                        "File is too large: " + props.size() + " bytes. Max allowed is " + MAX_FILE_SIZE + " bytes."));
                }
                return vertx.fileSystem().readFile(path)
                    .map(Buffer::toString)
                    .map(ToolInvokeResult::success);
            })
            .recover(err -> Future.succeededFuture(ToolInvokeResult.error("Error reading file: " + err.getMessage())));
    }

    private Future<ToolInvokeResult> write(Map<String, Object> args) {
        String path = (String) args.get("path");
        String content = (String) args.get("content");
        return vertx.fileSystem().writeFile(path, Buffer.buffer(content))
            .map(v -> ToolInvokeResult.success("Successfully written to " + path))
            .recover(err -> Future.succeededFuture(ToolInvokeResult.error("Error writing to file: " + err.getMessage())));
    }
}
