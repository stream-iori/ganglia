package work.ganglia.infrastructure.external.llm;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.codec.BodyCodec;

import work.ganglia.infrastructure.external.llm.util.JsonSanitizer;
import work.ganglia.infrastructure.external.llm.util.SseParser;
import work.ganglia.infrastructure.external.llm.util.SseWriteStream;
import work.ganglia.port.chat.Message;
import work.ganglia.port.chat.Role;
import work.ganglia.port.external.llm.ChatRequest;
import work.ganglia.port.external.llm.LLMException;
import work.ganglia.port.external.llm.ModelGateway;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.internal.state.ExecutionContext;
import work.ganglia.port.internal.state.TokenUsage;
import work.ganglia.util.AsyncConcurrencyLimiter;

/** Base class for ModelGateways to reduce boilerplate and enforce common constraints. */
public abstract class AbstractModelGateway implements ModelGateway {
  protected static final Logger logger = LoggerFactory.getLogger(AbstractModelGateway.class);

  /** Maximum concurrent LLM API calls allowed per gateway instance. */
  private static final int MAX_CONCURRENT_CALLS = 5;

  protected final Vertx vertx;
  private final AsyncConcurrencyLimiter limiter = new AsyncConcurrencyLimiter(MAX_CONCURRENT_CALLS);

  protected AbstractModelGateway(Vertx vertx) {
    this.vertx = vertx;
  }

  /** Emits a token received event via the ExecutionContext. */
  protected void publishToken(ExecutionContext context, String token) {
    if (token == null || token.isEmpty()) {
      return;
    }
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

  /** Wraps a future supplier with async concurrency limiting. */
  protected <T> Future<T> withLimit(Supplier<Future<T>> futureSupplier) {
    return limiter.withLimit(futureSupplier);
  }

  protected Throwable wrapException(Throwable e) {
    if (e instanceof LLMException) {
      return e;
    }
    return new LLMException(e.getMessage(), null, null, null, e);
  }

  /**
   * Appends {@code endpointPath} to {@code baseUrl}, handling trailing slashes and the case where
   * the path is already present.
   */
  protected static String normalizeEndpoint(String baseUrl, String endpointPath) {
    if (baseUrl.endsWith(endpointPath)) {
      return baseUrl;
    }
    return baseUrl.endsWith("/") ? baseUrl + endpointPath : baseUrl + "/" + endpointPath;
  }

  /** Collects streaming tool call builders into a finished list. */
  protected static List<ToolCall> collectToolCalls(Map<Integer, ToolCallBuilder> toolCallBuilders) {
    return toolCallBuilders.values().stream()
        .map(ToolCallBuilder::build)
        .collect(Collectors.toList());
  }

  /** Builds a {@link ModelResponse} from streaming accumulators. */
  protected static ModelResponse buildStreamingResponse(
      StringBuilder fullContent,
      Map<Integer, ToolCallBuilder> toolCallBuilders,
      int inputTokens,
      int outputTokens) {
    return new ModelResponse(
        fullContent.toString(),
        collectToolCalls(toolCallBuilders),
        new TokenUsage(inputTokens, outputTokens));
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

    SseParser parser =
        new SseParser(
            json -> {
              if (!request.signal().isAborted()) {
                eventHandler.accept(json);
              }
            },
            promise::tryFail);
    SseWriteStream writeStream = new SseWriteStream(parser);
    writeStream.exceptionHandler(promise::tryFail);

    // Active cancellation: fail the promise immediately when abort signal is received
    request
        .signal()
        .onAbort(
            () -> {
              promise.tryFail(new work.ganglia.port.internal.state.AgentAbortedException());
            });

    withLimit(
            () ->
                httpRequest
                    .putHeader("Accept", "text/event-stream")
                    .as(BodyCodec.pipe(writeStream, true))
                    .sendJsonObject(payload))
        .onSuccess(
            response -> {
              if (request.signal().isAborted()) {
                promise.tryFail(new work.ganglia.port.internal.state.AgentAbortedException());
                return;
              }
              if (response.statusCode() >= 400) {
                String errorBody = response.bodyAsString();
                if (errorBody == null || errorBody.isEmpty()) {
                  errorBody = writeStream.getRawData();
                }
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
                promise.tryFail(new work.ganglia.port.internal.state.AgentAbortedException());
              } else {
                promise.tryFail(err);
              }
            });

    return promise.future();
  }
}
