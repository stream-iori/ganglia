package me.stream.ganglia.core.tools;

import io.vertx.core.Future;
import me.stream.ganglia.core.model.ToolCall;
import me.stream.ganglia.core.model.ToolDefinition;
import me.stream.ganglia.core.model.ToolInvokeResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of ToolExecutor that orchestrates built-in tool sets.
 */
public class DefaultToolExecutor implements ToolExecutor {
    private final VertxFileSystemTools vertxFsTools;
    private final BashFileSystemTools bashFsTools;

    public DefaultToolExecutor(ToolsFactory factory) {
        this.vertxFsTools = factory.getVertxFileSystemTools();
        this.bashFsTools = factory.getBashFileSystemTools();
    }

    @Override
    public Future<ToolInvokeResult> execute(ToolCall toolCall) {
        return switch (toolCall.toolName()) {
            case "jvm_ls" -> vertxFsTools.ls(toolCall.arguments());
            case "jvm_read" -> vertxFsTools.read(toolCall.arguments());
            case "ls" -> bashFsTools.ls(toolCall.arguments());
            case "cat" -> bashFsTools.cat(toolCall.arguments());
            default -> Future.succeededFuture(ToolInvokeResult.error("Unknown tool: " + toolCall.toolName()));
        };
    }

        @Override

        public List<ToolDefinition> getAvailableTools() {

            List<ToolDefinition> tools = new ArrayList<>();

            tools.addAll(vertxFsTools.getDefinitions());

            tools.addAll(bashFsTools.getDefinitions());

            return tools;

        }

    }

    