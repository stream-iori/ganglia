package me.stream.ganglia.core.tools;

import io.vertx.core.Future;
import me.stream.ganglia.core.model.ToolCall;
import me.stream.ganglia.core.model.ToolDefinition;

import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of ToolExecutor that orchestrates built-in tool sets.
 */
public class DefaultToolExecutor implements ToolExecutor {
    private final FileSystemTools fsTools;

    public DefaultToolExecutor(ToolsFactory factory) {
        this.fsTools = factory.getFileSystemTools();
    }

    @Override
    public Future<String> execute(ToolCall toolCall) {
        switch (toolCall.toolName()) {
            case "ls":
                return fsTools.ls(toolCall.arguments());
            case "read":
                return fsTools.read(toolCall.arguments());
            default:
                return Future.failedFuture("Unknown tool: " + toolCall.toolName());
        }
    }

    @Override
    public List<ToolDefinition> getAvailableTools() {
        List<ToolDefinition> tools = new ArrayList<>();
        tools.addAll(fsTools.getDefinitions());
        return tools;
    }
}
