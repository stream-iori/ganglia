package me.stream.ganglia.core.tools;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import me.stream.ganglia.core.model.ToolCall;
import me.stream.ganglia.core.model.ToolDefinition;
import me.stream.ganglia.core.model.ToolType;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Built-in tools for local filesystem operations.
 */
public class FileSystemTools {
    private final Vertx vertx;

    public FileSystemTools(Vertx vertx) {
        this.vertx = vertx;
    }

    public List<ToolDefinition> getDefinitions() {
        return List.of(
            new ToolDefinition("ls", "List files in a directory", 
                "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"}},\"required\":[\"path\"]}", 
                ToolType.BUILTIN),
            new ToolDefinition("read", "Read content of a file", 
                "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"}},\"required\":[\"path\"]}", 
                ToolType.BUILTIN)
        );
    }

    public Future<String> ls(Map<String, Object> args) {
        String path = (String) args.get("path");
        return vertx.executeBlocking(() -> {
            File dir = new File(path);
            File[] files = dir.listFiles();
            if (files == null) return "Error: Directory not found or not accessible: " + path;
            return Arrays.stream(files)
                    .map(f -> f.isDirectory() ? f.getName() + "/" : f.getName())
                    .collect(Collectors.joining("\n"));
        });
    }

    public Future<String> read(Map<String, Object> args) {
        String path = (String) args.get("path");
        return vertx.executeBlocking(() -> {
            try {
                return Files.readString(new File(path).toPath());
            } catch (Exception e) {
                return "Error reading file: " + e.getMessage();
            }
        });
    }
}
