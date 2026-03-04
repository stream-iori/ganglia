package work.ganglia.core.llm;

import io.vertx.core.Future;
import work.ganglia.core.model.Message;
import work.ganglia.core.model.ModelOptions;
import work.ganglia.core.model.ModelResponse;
import work.ganglia.tools.model.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    public Future<ModelResponse> chat(List<Message> history, List<ToolDefinition> availableTools, ModelOptions options) {
        return primary.chat(history, availableTools, options)
            .recover(err -> {
                if (shouldFallback(err)) {
                    logger.warn("Primary model failed, falling back to utility model {}. Error: {}",
                        utilityModelName, err.getMessage());
                    ModelOptions fallbackOptions = new ModelOptions(
                        options.temperature(),
                        options.maxTokens(),
                        utilityModelName
                    );
                    return utility.chat(history, availableTools, fallbackOptions);
                }
                return Future.failedFuture(err);
            });
    }

    @Override
    public Future<ModelResponse> chatStream(List<Message> history, List<ToolDefinition> availableTools, ModelOptions options, String sessionId) {
        return primary.chatStream(history, availableTools, options, sessionId)
            .recover(err -> {
                if (shouldFallback(err)) {
                    logger.warn("Primary model stream failed, falling back to utility model {}. Error: {}",
                        utilityModelName, err.getMessage());
                    ModelOptions fallbackOptions = new ModelOptions(
                        options.temperature(),
                        options.maxTokens(),
                        utilityModelName
                    );
                    return utility.chatStream(history, availableTools, fallbackOptions, sessionId);
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
