package me.stream.ganglia.core.tools;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import me.stream.ganglia.core.tools.model.ToolDefinition;
import me.stream.ganglia.core.tools.model.ToolInvokeResult;
import me.stream.ganglia.core.tools.model.ToolType;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Built-in tools for local filesystem operations using JVM/Vert.x APIs.
 */
public class VertxFileSystemTools implements ToolSet {
    private final Vertx vertx;

    public VertxFileSystemTools(Vertx vertx) {
        this.vertx = vertx;
    }

    @Override
    public List<ToolDefinition> getDefinitions() {
        return List.of(
            new ToolDefinition("vertx_ls", "List files in a directory using JVM API",
                "{\n  \"type\": \"object\",\n  \"properties\": {\n    \"path\": {\n      \"type\": \"string\",\n      \"description\": \"The directory path to list\"\n    }\n  },\n  \"required\": [\"path\"]\n}",
                ToolType.BUILTIN),
            new ToolDefinition("vertx_read", "Read content of a file using JVM API",
                "{\n  \"type\": \"object\",\n  \"properties\": {\n    \"path\": {\n      \"type\": \"string\",\n      \"description\": \"The file path to read\"\n    }\n  },\n  \"required\": [\"path\"]\n}",
                ToolType.BUILTIN)
        );
    }

    @Override
    public Future<ToolInvokeResult> execute(String toolName, Map<String, Object> args, me.stream.ganglia.core.model.SessionContext context) {
        return switch (toolName) {
            case "vertx_ls" -> ls(args);
            case "vertx_read" -> read(args);
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
        return vertx.fileSystem().readFile(path)
            .map(Buffer::toString)
            .map(ToolInvokeResult::success)
            .recover(err -> Future.succeededFuture(ToolInvokeResult.error("Error reading file: " + err.getMessage())));
    }
}
