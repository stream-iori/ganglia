package me.stream.ganglia.core.tools;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import me.stream.ganglia.core.model.ToolDefinition;
import me.stream.ganglia.core.model.ToolType;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Built-in tools for local filesystem operations using Vert.x non-blocking APIs.
 */
public class FileSystemTools {
    private final Vertx vertx;

    public FileSystemTools(Vertx vertx) {
        this.vertx = vertx;
    }

    public List<ToolDefinition> getDefinitions() {
        return List.of(
            new ToolDefinition("ls", "List files in a directory", 
                "{\n  \"type\": \"object\",\n  \"properties\": {\n    \"path\": {\n      \"type\": \"string\",\n      \"description\": \"The directory path to list\"\n    }\n  },\n  \"required\": [\"path\"]\n}", 
                ToolType.BUILTIN),
            new ToolDefinition("read", "Read content of a file", 
                "{\n  \"type\": \"object\",\n  \"properties\": {\n    \"path\": {\n      \"type\": \"string\",\n      \"description\": \"The file path to read\"\n    }\n  },\n  \"required\": [\"path\"]\n}", 
                ToolType.BUILTIN)
        );
    }

    public Future<String> ls(Map<String, Object> args) {
        String path = (String) args.get("path");
        return vertx.fileSystem().readDir(path)
                .map(files -> files.stream()
                        .map(f -> {
                            // Vert.x readDir returns absolute paths. We strip them for cleaner output.
                            // But for simplicity, we just return the name component.
                            File file = new File(f);
                            return file.isDirectory() ? file.getName() + "/" : file.getName();
                        })
                        .collect(Collectors.joining("\n")))
                .recover(err -> Future.succeededFuture("Error listing directory: " + err.getMessage()));
    }

    public Future<String> read(Map<String, Object> args) {
        String path = (String) args.get("path");
        return vertx.fileSystem().readFile(path)
                .map(Buffer::toString)
                .recover(err -> Future.succeededFuture("Error reading file: " + err.getMessage()));
    }
}