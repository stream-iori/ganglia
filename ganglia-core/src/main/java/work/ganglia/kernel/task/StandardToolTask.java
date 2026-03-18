package work.ganglia.kernel.task;

import io.vertx.core.Future;
import work.ganglia.infrastructure.external.tool.model.ToolInvokeResult;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.external.tool.ToolExecutor;
import work.ganglia.port.internal.memory.MemoryStore;
import work.ganglia.port.internal.memory.ObservationCompressor;
import work.ganglia.port.internal.memory.model.CompressionContext;
import work.ganglia.port.internal.memory.model.MemoryCategory;
import work.ganglia.port.internal.memory.model.MemoryEntry;
import work.ganglia.port.internal.state.ExecutionContext;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

/**
 * A scheduleable task that wraps a standard tool execution (e.g. Bash, FileSystem).
 */
public class StandardToolTask implements AgentTask {
    private final ToolCall toolCall;
    private final ToolExecutor toolExecutor;
    private final ObservationCompressor observationCompressor;
    private final MemoryStore memoryStore;

    public StandardToolTask(ToolCall toolCall, ToolExecutor toolExecutor, 
                            ObservationCompressor observationCompressor, MemoryStore memoryStore) {
        this.toolCall = toolCall;
        this.toolExecutor = toolExecutor;
        this.observationCompressor = observationCompressor;
        this.memoryStore = memoryStore;
    }

    @Override
    public String id() {
        return toolCall.id();
    }

    @Override
    public String name() {
        return toolCall.toolName();
    }

    @Override
    public Future<AgentTaskResult> execute(SessionContext context, ExecutionContext executionContext) {
        return toolExecutor.execute(toolCall, context, executionContext)
            .compose(invokeResult -> {
                String rawOutput = invokeResult.output();
                if (observationCompressor != null && memoryStore != null && 
                    invokeResult.status() == ToolInvokeResult.Status.SUCCESS && 
                    !"recall_memory".equals(toolCall.toolName()) &&
                    observationCompressor.requiresCompression(rawOutput)) {
                    
                    CompressionContext compCtx = new CompressionContext(toolCall.toolName(), "Tool execution observation", 1024);
                    return observationCompressor.compress(rawOutput, compCtx)
                        .compose(summary -> {
                            String memoryId = UUID.randomUUID().toString().substring(0, 8);
                            MemoryEntry entry = new MemoryEntry(
                                memoryId,
                                "Compressed Output: " + toolCall.toolName(),
                                summary,
                                rawOutput,
                                MemoryCategory.OBSERVATION,
                                Collections.emptyList(),
                                Instant.now(),
                                Collections.emptyList()
                            );
                            return memoryStore.store(entry).map(v -> {
                                String newOutput = "Output was very long and has been compressed. ID: " + memoryId + ".\nSummary: " + summary + "\nUse recall_memory tool to view full content.";
                                return new ToolInvokeResult(newOutput, invokeResult.status(), invokeResult.errorDetails(), invokeResult.modifiedContext(), invokeResult.diff());
                            });
                        });
                }
                return Future.succeededFuture(invokeResult);
            })
            .map(invokeResult -> {
                AgentTaskResult.Status status = switch (invokeResult.status()) {
                    case SUCCESS -> AgentTaskResult.Status.SUCCESS;
                    case ERROR -> AgentTaskResult.Status.ERROR;
                    case EXCEPTION -> AgentTaskResult.Status.EXCEPTION;
                    case INTERRUPT -> AgentTaskResult.Status.INTERRUPT;
                };
                return new AgentTaskResult(status, invokeResult.output(), invokeResult.modifiedContext());
            });
    }
}
