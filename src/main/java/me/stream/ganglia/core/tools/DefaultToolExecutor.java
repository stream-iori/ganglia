package me.stream.ganglia.core.tools;

import io.vertx.core.Future;
import me.stream.ganglia.core.tools.model.ToolCall;
import me.stream.ganglia.core.tools.model.ToolDefinition;
import me.stream.ganglia.core.tools.model.ToolInvokeResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of ToolExecutor that orchestrates built-in tool sets.
 */
public class DefaultToolExecutor implements ToolExecutor {
        private final VertxFileSystemTools vertxFsTools;
        private final BashFileSystemTools bashFsTools;
        private final ToDoTools toDoTools;

        public DefaultToolExecutor(ToolsFactory factory) {
            this.vertxFsTools = factory.getVertxFileSystemTools();
            this.bashFsTools = factory.getBashFileSystemTools();
            this.toDoTools = factory.getToDoTools();
        }

        @Override
        public Future<ToolInvokeResult> execute(ToolCall toolCall, me.stream.ganglia.core.model.SessionContext context) {
            return switch (toolCall.toolName()) {
                case "jvm_ls" -> vertxFsTools.ls(toolCall.arguments());
                case "jvm_read" -> vertxFsTools.read(toolCall.arguments());
                case "ls" -> bashFsTools.ls(toolCall.arguments());
                case "cat" -> bashFsTools.cat(toolCall.arguments());
                case "todo_add" -> toDoTools.add(toolCall.arguments(), context);
                case "todo_list" -> toDoTools.list(context);
                case "todo_complete" -> toDoTools.complete(toolCall.arguments(), context);
                default -> Future.succeededFuture(ToolInvokeResult.error("Unknown tool: " + toolCall.toolName()));
            };
        }

        @Override
        public List<ToolDefinition> getAvailableTools() {
            List<ToolDefinition> tools = new ArrayList<>();
            tools.addAll(vertxFsTools.getDefinitions());
            tools.addAll(bashFsTools.getDefinitions());
            tools.addAll(toDoTools.getDefinitions());
            return tools;
        }
    }


