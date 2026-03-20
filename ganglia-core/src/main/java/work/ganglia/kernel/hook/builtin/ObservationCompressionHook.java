package work.ganglia.kernel.hook.builtin;

import io.vertx.core.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.ganglia.infrastructure.external.tool.model.ToolInvokeResult;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.internal.hook.AgentInterceptor;
import work.ganglia.port.internal.memory.MemoryStore;
import work.ganglia.port.internal.memory.ObservationCompressor;
import work.ganglia.port.internal.memory.model.CompressionContext;
import work.ganglia.port.internal.memory.model.MemoryCategory;
import work.ganglia.port.internal.memory.model.MemoryEntry;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

/**
 * Built-in hook to intercept large tool outputs, compress them using an LLM,
 * and store the full content in the MemoryStore for progressive disclosure.
 */
public class ObservationCompressionHook implements AgentInterceptor {
    private static final Logger log = LoggerFactory.getLogger(ObservationCompressionHook.class);
    
    private final ObservationCompressor observationCompressor;
    private final MemoryStore memoryStore;

    public ObservationCompressionHook(ObservationCompressor observationCompressor, MemoryStore memoryStore) {
        this.observationCompressor = observationCompressor;
        this.memoryStore = memoryStore;
    }

    @Override
    public Future<ToolInvokeResult> postToolExecute(ToolCall call, ToolInvokeResult result, SessionContext context) {
        if (observationCompressor == null || memoryStore == null) {
            return Future.succeededFuture(result);
        }

        if (result.status() != ToolInvokeResult.Status.SUCCESS) {
            return Future.succeededFuture(result);
        }

        if (call == null) {
            return Future.succeededFuture(result);
        }

        if ("recall_memory".equals(call.toolName())) {
            return Future.succeededFuture(result);
        }

        String rawOutput = result.output();
        if (!observationCompressor.requiresCompression(rawOutput)) {
            return Future.succeededFuture(result);
        }

        log.debug("Intercepted large output from tool '{}', triggering compression.", call.toolName());
        CompressionContext compCtx = new CompressionContext(call.toolName(), "Tool execution observation", 1024);
        
        return observationCompressor.compress(rawOutput, compCtx)
            .compose(summary -> {
                String memoryId = UUID.randomUUID().toString().substring(0, 8);
                MemoryEntry entry = new MemoryEntry(
                    memoryId,
                    "Compressed Output: " + call.toolName(),
                    summary,
                    rawOutput,
                    MemoryCategory.OBSERVATION,
                    Collections.emptyList(),
                    Instant.now(),
                    Collections.emptyList()
                );
                return memoryStore.store(entry).map(v -> {
                    String newOutput = "Output was very long and has been compressed. ID: " + memoryId + ".\nSummary: " + summary + "\nUse recall_memory tool to view full content.";
                    return new ToolInvokeResult(newOutput, result.status(), result.errorDetails(), result.modifiedContext(), result.diff());
                });
            })
            .recover(err -> {
                log.warn("Observation compression failed, falling back to raw output", err);
                return Future.succeededFuture(result);
            });
    }
}