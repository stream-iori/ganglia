package work.ganglia.infrastructure.external.llm;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.codec.BodyCodec;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.ganglia.infrastructure.external.llm.util.JsonSanitizer;
import work.ganglia.infrastructure.external.llm.util.SseParser;
import work.ganglia.infrastructure.external.llm.util.SseWriteStream;
import work.ganglia.port.chat.Message;
import work.ganglia.port.chat.Role;
import work.ganglia.port.external.llm.ChatRequest;
import work.ganglia.port.external.llm.ModelGateway;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.internal.state.ExecutionContext;

/** Base class for ModelGateways to reduce boilerplate and enforce common constraints. */
public abstract class AbstractModelGateway implements ModelGateway {
  protected static final Logger logger = LoggerFactory.getLogger(AbstractModelGateway.class);
  protected final Vertx vertx;
  private final Semaphore semaphore = new Semaphore(5); // Limit to 5 concurrent calls

  protected AbstractModelGateway(Vertx vertx) {
    this.vertx = vertx;
  }

  /** Emits a token received event via the ExecutionContext. */
  protected void publishToken(ExecutionContext context, String token) {
    if (token == null || token.isEmpty()) return;
    if (context != null) {
      context.emitStream(token);
    }
  }

  /** Common logic to merge multiple system messages into one if needed. */
  protected String mergeSystemMessages(java.util.List<Message> history) {
    return history.stream()
        .filter(m -> m.role() == Role.SYSTEM)
        .map(Message::content)
        .reduce((a, b) -> a + "\n" + b)
        .orElse(null);
  }

  /** Wraps a future with semaphore protection to limit concurrency. */
  protected <T> Future<T> withSemaphore(Future<T> future) {
    if (semaphore.tryAcquire()) {
      return future.onComplete(v -> semaphore.release());
    } else {
      return Future.failedFuture(
          new LLMException("Concurrency limit reached (max 5)", null, 429, null, null));
    }
  }

  protected Throwable wrapException(Throwable e) {
    if (e instanceof LLMException) return e;
    return new LLMException(e.getMessage(), null, null, null, e);
  }

  /** Shared helper for ToolCall building during streaming. */
  protected static class ToolCallBuilder {
    public String id;
    public String name;
    public final StringBuilder arguments = new StringBuilder();

    public ToolCall build() {
      String argStr = arguments.toString();
      Map<String, Object> argsMap = Collections.emptyMap();
      if (!argStr.isEmpty()) {
        try {
          String sanitized = JsonSanitizer.sanitize(argStr);
          argsMap = new JsonObject(sanitized).getMap();
        } catch (Exception e) {
          logger.error("Failed to parse tool call arguments: {}", argStr, e);
        }
      }
      return new ToolCall(
          id != null ? id : "call_" + UUID.randomUUID().toString().substring(0, 8), name, argsMap);
    }
  }

  /** Template method for streaming requests. */
  protected Future<ModelResponse> executeStreamingRequest(
      ChatRequest request,
      ExecutionContext context,
      JsonObject payload,
      HttpRequest<Buffer> httpRequest,
      Consumer<JsonObject> eventHandler,
      Supplier<ModelResponse> responseSupplier) {

    Promise<ModelResponse> promise = Promise.promise();

    SseParser parser = new SseParser(eventHandler::accept, promise::tryFail);
    SseWriteStream writeStream = new SseWriteStream(parser);
    writeStream.exceptionHandler(promise::tryFail);

    // Active cancellation: fail the promise immediately when abort signal is received
    request
        .signal()
        .onAbort(
            () -> {
              promise.tryFail(new work.ganglia.kernel.loop.AgentAbortedException());
            });

    withSemaphore(
            httpRequest
                .putHeader("Accept", "text/event-stream")
                .as(BodyCodec.pipe(writeStream, true))
                .sendJsonObject(payload))
        .onSuccess(
            response -> {
              if (request.signal().isAborted()) {
                promise.tryFail(new work.ganglia.kernel.loop.AgentAbortedException());
                return;
              }
              if (response.statusCode() >= 400) {
                String errorBody = response.bodyAsString();
                logger.error("[LLM_ERROR] Status: {}, Body: {}", response.statusCode(), errorBody);
                promise.fail(
                    new LLMException(
                        "LLM Error: " + response.statusCode() + " " + response.statusMessage(),
                        null,
                        response.statusCode(),
                        errorBody,
                        null));
              } else {
                try {
                  promise.complete(responseSupplier.get());
                } catch (Exception e) {
                  promise.fail(wrapException(e));
                }
              }
            })
        .onFailure(
            err -> {
              if (request.signal().isAborted()) {
                promise.tryFail(new work.ganglia.kernel.loop.AgentAbortedException());
              } else {
                promise.tryFail(err);
              }
            });

    return promise.future();
  }
}
