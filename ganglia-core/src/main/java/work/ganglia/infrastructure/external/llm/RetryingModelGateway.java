package work.ganglia.infrastructure.external.llm;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.ganglia.port.chat.Message;
import work.ganglia.port.external.llm.ModelOptions;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.internal.state.AgentSignal;
import work.ganglia.port.external.tool.ToolDefinition;
import work.ganglia.port.external.llm.ModelGateway;

import java.util.List;

/**
 * Decorator for ModelGateway that adds network resilience (retry with exponential backoff).
 */
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
    public Future<ModelResponse> chat(List<Message> history, List<ToolDefinition> availableTools, ModelOptions options, AgentSignal signal) {
        return retryChat(history, availableTools, options, signal, 0);
    }

    @Override
    public Future<ModelResponse> chatStream(List<Message> history, List<ToolDefinition> availableTools, ModelOptions options, String sessionId, AgentSignal signal) {
        return retryChatStream(history, availableTools, options, sessionId, signal, 0);
    }

    private Future<ModelResponse> retryChat(List<Message> history, List<ToolDefinition> availableTools, ModelOptions options, AgentSignal signal, int attempt) {
        if (signal.isAborted()) return Future.failedFuture(new work.ganglia.kernel.loop.AgentAbortedException());
        Future<ModelResponse> future = delegate.chat(history, availableTools, options, signal);
        if (future == null) {
            return Future.failedFuture("Delegate gateway returned null future for chat");
        }
        return future.recover(err -> handleRetry(err, attempt, () -> retryChat(history, availableTools, options, signal, attempt + 1)));
    }

    private Future<ModelResponse> retryChatStream(List<Message> history, List<ToolDefinition> availableTools, ModelOptions options, String sessionId, AgentSignal signal, int attempt) {
        if (signal.isAborted()) return Future.failedFuture(new work.ganglia.kernel.loop.AgentAbortedException());
        Future<ModelResponse> future = delegate.chatStream(history, availableTools, options, sessionId, signal);
        if (future == null) {
            return Future.failedFuture("Delegate gateway returned null future for chatStream");
        }
        return future.recover(err -> handleRetry(err, attempt, () -> retryChatStream(history, availableTools, options, sessionId, signal, attempt + 1)));
    }

    private Future<ModelResponse> handleRetry(Throwable err, int attempt, java.util.function.Supplier<Future<ModelResponse>> nextAttempt) {
        if (shouldRetry(err) && attempt < maxRetries) {
            long delay = calculateDelay(attempt);
            //TODO: retry 信息要透入出去,让用户感知
            logger.warn("Transient LLM error. Retrying in {}ms... Error: {}", delay, err.getMessage());

            Promise<ModelResponse> promise = Promise.promise();
            vertx.setTimer(delay, id -> nextAttempt.get().onComplete(promise));
            return promise.future();
        }
        return Future.failedFuture(err);
    }

    private boolean shouldRetry(Throwable err) {
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
