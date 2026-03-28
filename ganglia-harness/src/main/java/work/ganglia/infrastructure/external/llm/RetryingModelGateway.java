package work.ganglia.infrastructure.external.llm;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

import work.ganglia.port.external.llm.ChatRequest;
import work.ganglia.port.external.llm.ModelGateway;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.external.tool.ObservationType;
import work.ganglia.port.internal.state.ExecutionContext;
import work.ganglia.port.internal.state.ObservationDispatcher;

/** Decorator for ModelGateway that adds network resilience (retry with exponential backoff). */
public class RetryingModelGateway implements ModelGateway {
  private static final Logger logger = LoggerFactory.getLogger(RetryingModelGateway.class);

  private final ModelGateway delegate;
  private final Vertx vertx;
  private final int maxRetries;
  private volatile ObservationDispatcher dispatcher;

  /** Late-bind the dispatcher to avoid constructor ordering issues in GangliaKernel. */
  public void setDispatcher(ObservationDispatcher dispatcher) {
    this.dispatcher = dispatcher;
  }

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
    if (request.signal().isAborted()) {
      return Future.failedFuture(new work.ganglia.kernel.loop.AgentAbortedException());
    }
    return delegate
        .chat(request)
        .recover(err -> handleRetry(err, attempt, () -> retryChat(request, attempt + 1), null));
  }

  private Future<ModelResponse> retryChatStream(
      ChatRequest request, ExecutionContext context, int attempt) {
    if (request.signal().isAborted()) {
      return Future.failedFuture(new work.ganglia.kernel.loop.AgentAbortedException());
    }

    if (dispatcher != null) {
      Map<String, Object> data = new HashMap<>();
      data.put("model", request.options().modelName());
      data.put("attempt", attempt + 1);
      data.put("streaming", true);
      dispatcher.dispatch(
          context.sessionId(),
          ObservationType.MODEL_CALL_STARTED,
          request.options().modelName(),
          data);
    }
    long startMs = System.currentTimeMillis();

    return delegate
        .chatStream(request, context)
        .map(
            response -> {
              if (dispatcher != null) {
                Map<String, Object> data = new HashMap<>();
                data.put("model", request.options().modelName());
                data.put("attempt", attempt + 1);
                data.put("durationMs", System.currentTimeMillis() - startMs);
                data.put("status", "success");
                dispatcher.dispatch(
                    context.sessionId(),
                    ObservationType.MODEL_CALL_FINISHED,
                    request.options().modelName(),
                    data);
              }
              return response;
            })
        .recover(
            err -> {
              if (dispatcher != null && (!shouldRetry(err) || attempt >= maxRetries)) {
                Map<String, Object> data = new HashMap<>();
                data.put("model", request.options().modelName());
                data.put("attempt", attempt + 1);
                data.put("durationMs", System.currentTimeMillis() - startMs);
                data.put("status", "failed");
                data.put("error", err.getMessage());
                dispatcher.dispatch(
                    context.sessionId(),
                    ObservationType.MODEL_CALL_FINISHED,
                    request.options().modelName(),
                    data);
              }
              return handleRetry(
                  err, attempt, () -> retryChatStream(request, context, attempt + 1), context);
            });
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
        String retryNotification =
            String.format(
                "\n\n⚠️ Network error: %s. Retrying attempt %d of %d...\n\n",
                err.getMessage(), attempt + 1, maxRetries);
        context.emitStream(retryNotification);
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

    if (cause instanceof work.ganglia.kernel.loop.AgentAbortedException) {
      return false;
    }

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
