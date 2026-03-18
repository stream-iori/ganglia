package work.ganglia.infrastructure.external.tool;

import io.vertx.core.Future;
import work.ganglia.infrastructure.external.tool.model.ToolInvokeResult;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ToolDefinition;
import work.ganglia.port.external.tool.ToolSet;
import work.ganglia.port.internal.memory.MemoryStore;

import java.util.List;
import java.util.Map;

public class RecallMemoryTools implements ToolSet {

    private final MemoryStore memoryStore;

    public RecallMemoryTools(MemoryStore memoryStore) {
        this.memoryStore = memoryStore;
    }

    @Override
    public List<ToolDefinition> getDefinitions() {
        return List.of(
            new ToolDefinition("recall_memory", "Fetch the full context of a memory index item using its ID.",
                """
                {
                  "type": "object",
                  "properties": {
                    "id": { "type": "string", "description": "The unique ID of the memory item to recall" }
                  },
                  "required": ["id"]
                }
                """)
        );
    }

    @Override
    public Future<ToolInvokeResult> execute(String toolName, Map<String, Object> args, SessionContext context, work.ganglia.port.internal.state.ExecutionContext executionContext) {
        if ("recall_memory".equals(toolName)) {
            return recallMemory(args);
        }
        return Future.succeededFuture(ToolInvokeResult.error("Unknown tool: " + toolName));
    }

    private Future<ToolInvokeResult> recallMemory(Map<String, Object> args) {
        String id = (String) args.get("id");
        if (id == null || id.isBlank()) {
            return Future.succeededFuture(ToolInvokeResult.error("Memory ID is required"));
        }

        return memoryStore.recall(id)
            .map(entry -> ToolInvokeResult.success("Memory Details:\n" +
                "Title: " + entry.title() + "\n" +
                "Category: " + entry.category() + "\n" +
                "Timestamp: " + entry.timestamp() + "\n\n" +
                "Content:\n" + entry.fullContent()))
            .recover(err -> Future.succeededFuture(ToolInvokeResult.error("Failed to recall memory " + id + ": " + err.getMessage())));
    }
}