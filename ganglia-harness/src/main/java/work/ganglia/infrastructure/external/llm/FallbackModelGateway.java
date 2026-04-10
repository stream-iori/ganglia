package work.ganglia.infrastructure.external.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;

import work.ganglia.port.external.llm.ChatRequest;
import work.ganglia.port.external.llm.LLMException;
import work.ganglia.port.external.llm.ModelGateway;
import work.ganglia.port.external.llm.ModelOptions;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.internal.state.ExecutionContext;

/** A ModelGateway that automatically falls back to a utility model if the primary model fails. */
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
  public Future<ModelResponse> chat(ChatRequest request) {
    return primary
        .chat(request)
        .recover(
            err -> {
              if (shouldFallback(err)) {
                logger.warn(
                    "Primary model failed, falling back to utility model {}. Error: {}",
                    utilityModelName,
                    err.getMessage());
                ChatRequest fallbackRequest = createFallbackRequest(request);
                return utility.chat(fallbackRequest);
              }
              return Future.failedFuture(err);
            });
  }

  @Override
  public Future<ModelResponse> chatStream(ChatRequest request, ExecutionContext context) {
    return primary
        .chatStream(request, context)
        .recover(
            err -> {
              if (shouldFallback(err)) {
                logger.warn(
                    "Primary model stream failed, falling back to utility model {}. Error: {}",
                    utilityModelName,
                    err.getMessage());
                ChatRequest fallbackRequest = createFallbackRequest(request);
                return utility.chatStream(fallbackRequest, context);
              }
              return Future.failedFuture(err);
            });
  }

  private ChatRequest createFallbackRequest(ChatRequest original) {
    ModelOptions originalOptions = original.options();
    ModelOptions fallbackOptions =
        new ModelOptions(
            originalOptions.temperature(),
            originalOptions.maxTokens(),
            utilityModelName,
            originalOptions.stream());
    return new ChatRequest(
        original.messages(), original.tools(), fallbackOptions, original.signal());
  }

  private boolean shouldFallback(Throwable err) {
    if (err instanceof LLMException le) {
      int status = le.httpStatusCode().orElse(0);
      // Fallback on rate limit or server errors
      return status == 429 || (status >= 500 && status < 600);
    }
    return false;
  }
}
