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
    private final JVMFileSystemTools jvmFsTools;
    private final BashFileSystemTools bashFsTools;

    public DefaultToolExecutor(ToolsFactory factory) {
        this.jvmFsTools = factory.getJvmFileSystemTools();
        this.bashFsTools = factory.getBashFileSystemTools();
    }

    @Override
    public Future<String> execute(ToolCall toolCall) {
        return switch (toolCall.toolName()) {
            case "jvm_ls" -> jvmFsTools.ls(toolCall.arguments());
            case "jvm_read" -> jvmFsTools.read(toolCall.arguments());
            case "ls" -> bashFsTools.ls(toolCall.arguments());
            case "cat" -> bashFsTools.cat(toolCall.arguments());
            default -> Future.failedFuture("Unknown tool: " + toolCall.toolName());
        };
    }

    @Override
    public List<ToolDefinition> getAvailableTools() {
        List<ToolDefinition> tools = new ArrayList<>();
        tools.addAll(jvmFsTools.getDefinitions());
        tools.addAll(bashFsTools.getDefinitions());
        return tools;
    }
}