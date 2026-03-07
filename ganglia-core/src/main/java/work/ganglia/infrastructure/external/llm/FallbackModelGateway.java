package work.ganglia.infrastructure.external.llm;

import io.vertx.core.Future;
import work.ganglia.port.chat.Message;
import work.ganglia.port.external.llm.ModelOptions;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.internal.state.AgentSignal;
import work.ganglia.port.external.tool.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.ganglia.port.external.llm.ModelGateway;

import java.util.List;

/**
 * A ModelGateway that automatically falls back to a utility model if the primary model fails.
 */
public class FallbackModelGateway implements ModelGateway {
    private static final Logger logger = LoggerFactory.getLogger(FallbackModelGateway.class);

    private final ModelGateway primary;
    private final ModelGateway utility;
    private final String utilityModelName;

    public FallbackModelGateway(ModelGateway primary, ModelGateway utility, String utilityModelName) {
        this.primary = primary;
        this.utility = utility;
        this.utilityModelName = utilityModelName;
    }

    @Override
    public Future<ModelResponse> chat(List<Message> history, List<ToolDefinition> availableTools, ModelOptions options, AgentSignal signal) {
        return primary.chat(history, availableTools, options, signal)
            .recover(err -> {
                if (shouldFallback(err)) {
                    logger.warn("Primary model failed, falling back to utility model {}. Error: {}",
                        utilityModelName, err.getMessage());
                    ModelOptions fallbackOptions = new ModelOptions(
                        options.temperature(),
                        options.maxTokens(),
                        utilityModelName,
                        options.stream()
                    );
                    return utility.chat(history, availableTools, fallbackOptions, signal);
                }
                return Future.failedFuture(err);
            });
    }

    @Override
    public Future<ModelResponse> chatStream(List<Message> history, List<ToolDefinition> availableTools, ModelOptions options, String sessionId, AgentSignal signal) {
        return primary.chatStream(history, availableTools, options, sessionId, signal)
            .recover(err -> {
                if (shouldFallback(err)) {
                    logger.warn("Primary model stream failed, falling back to utility model {}. Error: {}",
                        utilityModelName, err.getMessage());
                    ModelOptions fallbackOptions = new ModelOptions(
                        options.temperature(),
                        options.maxTokens(),
                        utilityModelName,
                        options.stream()
                    );
                    return utility.chatStream(history, availableTools, fallbackOptions, sessionId, signal);
                }
                return Future.failedFuture(err);
            });
    }

    private boolean shouldFallback(Throwable err) {
        if (err instanceof LLMException) {
            LLMException le = (LLMException) err;
            int status = le.httpStatusCode().orElse(0);
            // Fallback on rate limit or server errors
            return status == 429 || (status >= 500 && status < 600);
        }
        return false;
    }
}
