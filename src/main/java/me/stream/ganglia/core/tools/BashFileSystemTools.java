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
                Process process = Runtime.getRuntime().exec(new String[]{"/bin/bash", "-c", command});
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String output = reader.lines().collect(Collectors.joining("\n"));
                    process.waitFor();
                    if (process.exitValue() != 0) {
                        try (BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                            String error = errReader.lines().collect(Collectors.joining("\n"));
                            return "Error executing command: " + error;
                        }
                    }
                    return output;
                }
            } catch (Exception e) {
                return "Error executing bash: " + e.getMessage();
            }
        });
    }
}
