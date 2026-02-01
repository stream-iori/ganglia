package me.stream.ganglia.core.tools;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import me.stream.ganglia.core.model.ToolDefinition;
import me.stream.ganglia.core.model.ToolType;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Built-in tools for local filesystem operations using Bash commands.
 */
public class BashFileSystemTools {
    private final Vertx vertx;

    public BashFileSystemTools(Vertx vertx) {
        this.vertx = vertx;
    }

    public List<ToolDefinition> getDefinitions() {
        return List.of(
            new ToolDefinition("ls", "List files in a directory using bash ls",
                "{\n  \"type\": \"object\",\n  \"properties\": {\n    \"path\": {\n      \"type\": \"string\",\n      \"description\": \"The directory path to list\"\n    }\n  },\n  \"required\": [\"path\"]\n}",
                ToolType.BUILTIN),
            new ToolDefinition("cat", "Read content of a file using bash cat",
                "{\n  \"type\": \"object\",\n  \"properties\": {\n    \"path\": {\n      \"type\": \"string\",\n      \"description\": \"The file path to read\"\n    }\n  },\n  \"required\": [\"path\"]\n}",
                ToolType.BUILTIN)
        );
    }

    public Future<String> ls(Map<String, Object> args) {
        String path = (String) args.get("path");
        return executeBash("ls -F " + path);
    }

    public Future<String> cat(Map<String, Object> args) {
        String path = (String) args.get("path");
        return executeBash("cat " + path);
    }

    private Future<String> executeBash(String command) {
        return vertx.executeBlocking(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder("/bin/bash", "-c", command);
                pb.redirectErrorStream(true); // Combine stdout and stderr
                Process process = pb.start();
                
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String output = reader.lines().collect(Collectors.joining("\n"));
                    int exitCode = process.waitFor();
                    if (exitCode != 0) {
                        return "Error executing command (exit code " + exitCode + "): " + output;
                    }
                    return output;
                }
            } catch (Exception e) {
                return "Error executing bash: " + e.getMessage();
            }
        });
    }
}
