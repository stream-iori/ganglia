package me.stream.ganglia.it;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import me.stream.ganglia.core.model.ToolCall;
import me.stream.ganglia.core.model.ToolDefinition;
import me.stream.ganglia.core.tools.ToolExecutor;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class SystemToolExecutor implements ToolExecutor {
    private final Vertx vertx;

    public SystemToolExecutor(Vertx vertx) {
        this.vertx = vertx;
    }

    @Override
    public Future<String> execute(ToolCall toolCall) {
        System.out.println("IT DEBUG: Executing tool: " + toolCall.toolName() + " with args: " + toolCall.arguments());
        return vertx.executeBlocking(() -> {
            try {
                if ("ls".equals(toolCall.toolName())) {
                    String path = (String) toolCall.arguments().get("path");
                    File dir = new File(path);
                    File[] files = dir.listFiles();
                    if (files == null) {
                        System.err.println("IT DEBUG: Directory not found: " + path);
                        return "Directory not found: " + path;
                    }
                    String result = Arrays.stream(files).map(File::getName).collect(Collectors.joining("\n"));
                    System.out.println("IT DEBUG: ls result: " + result);
                    return result;
                } else if ("read".equals(toolCall.toolName())) {
                    String path = (String) toolCall.arguments().get("path");
                    String result = Files.readString(new File(path).toPath());
                    System.out.println("IT DEBUG: read result: " + result);
                    return result;
                }
                return "Unknown tool: " + toolCall.toolName();
            } catch (Exception e) {
                System.err.println("IT DEBUG: Tool error: " + e.getMessage());
                return "Error: " + e.getMessage();
            }
        });
    }

    @Override
    public List<ToolDefinition> getAvailableTools() {
        return List.of(
            new ToolDefinition("ls", "List files in a directory", "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"}}}"),
            new ToolDefinition("read", "Read content of a file", "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"}}}")
        );
    }
}
