package work.ganglia.infrastructure.external.llm;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.ganglia.port.external.llm.ChatRequest;
import work.ganglia.port.external.llm.ModelGateway;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.internal.state.ExecutionContext;

/** Decorator for ModelGateway that adds network resilience (retry with exponential backoff). */
public class RetryingModelGateway implements ModelGateway {
  private static final Logger logger = LoggerFactory.getLogger(RetryingModelGateway.class);

  private final ModelGateway delegate;
  private final Vertx vertx;
  private final int maxRetries;

  public RetryingModelGateway(ModelGateway delegate, Vertx vertx, int maxRetries) {
    this.delegate = delegate;
    this.vertx = vertx;
    this.maxRetries = maxRetries;
  }

  public RetryingModelGateway(ModelGateway delegate, Vertx vertx) {
    this(delegate, vertx, 3);
  }

  @Override
  public Future<ModelResponse> chat(ChatRequest request) {
    return retryChat(request, 0);
  }

  @Override
  public Future<ModelResponse> chatStream(ChatRequest request, ExecutionContext context) {
    return retryChatStream(request, context, 0);
  }

  private Future<ModelResponse> retryChat(ChatRequest request, int attempt) {
    if (request.signal().isAborted())
      return Future.failedFuture(new work.ganglia.kernel.loop.AgentAbortedException());
    return delegate
        .chat(request)
        .recover(err -> handleRetry(err, attempt, () -> retryChat(request, attempt + 1), null));
  }

  private Future<ModelResponse> retryChatStream(
      ChatRequest request, ExecutionContext context, int attempt) {
    if (request.signal().isAborted())
      return Future.failedFuture(new work.ganglia.kernel.loop.AgentAbortedException());
    return delegate
        .chatStream(request, context)
        .recover(
            err ->
                handleRetry(
                    err, attempt, () -> retryChatStream(request, context, attempt + 1), context));
  }

  private Future<ModelResponse> handleRetry(
      Throwable err,
      int attempt,
      java.util.function.Supplier<Future<ModelResponse>> nextAttempt,
      ExecutionContext context) {
    if (shouldRetry(err) && attempt < maxRetries) {
      long delay = calculateDelay(attempt);
      logger.warn(
          "Transient LLM error. Retrying attempt {}/{} in {}ms... Error: {}",
          attempt + 1,
          maxRetries,
          delay,
          err.getMessage());

      if (context != null) {
        String warning =
            String.format(
                "\n\n⚠️ Network error: %s. Retrying attempt %d of %d...\n\n",
                err.getMessage(), attempt + 1, maxRetries);
        context.emitStream(warning);
      }

      Promise<ModelResponse> promise = Promise.promise();
      vertx.setTimer(delay, id -> nextAttempt.get().onComplete(promise));
      return promise.future();
    }
    return Future.failedFuture(err);
  }

  private boolean shouldRetry(Throwable err) {
    // Unwrap Vert.x generic errors if possible
    Throwable cause = err.getCause() != null ? err.getCause() : err;
    logger.debug(
        "Checking if should retry. Error class: {}, Cause class: {}, Message: {}",
        err.getClass().getName(),
        cause.getClass().getName(),
        cause.getMessage());

    if (cause instanceof java.io.IOException
        || cause instanceof java.net.ConnectException
        || cause.getClass().getName().contains("TimeoutException")
        || (cause instanceof io.vertx.core.VertxException
            && cause.getMessage() != null
            && cause.getMessage().toLowerCase().contains("connection was closed"))) {
      return true;
    }

    if (err instanceof LLMException) {
      LLMException le = (LLMException) err;
      int status = le.httpStatusCode().orElse(0);
      return status == 429 || (status >= 500 && status < 600);
    }
    return false;
  }

  private long calculateDelay(int attempt) {
    long base = 1000; // 1s
    long delay = (long) (base * Math.pow(2, attempt));
    long jitter = (long) (Math.random() * 500); // 0-500ms jitter
    return delay + jitter;
  }
}
