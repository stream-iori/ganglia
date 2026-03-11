package work.ganglia.infrastructure.external.llm;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.codec.BodyCodec;
import work.ganglia.infrastructure.external.llm.util.JsonSanitizer;
import work.ganglia.infrastructure.external.llm.util.SseParser;
import work.ganglia.infrastructure.external.llm.util.SseWriteStream;
import work.ganglia.port.chat.Message;
import work.ganglia.port.external.llm.ModelOptions;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.internal.state.AgentSignal;
import work.ganglia.port.internal.state.TokenUsage;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.external.tool.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.ganglia.port.external.llm.ModelGateway;

import java.util.*;
import java.util.stream.Collectors;

public class OpenAIModelGateway extends AbstractModelGateway {
    private static final Logger logger = LoggerFactory.getLogger(OpenAIModelGateway.class);
    private final WebClient webClient;
    private final String apiKey;
    private final String endpoint;

    public OpenAIModelGateway(Vertx vertx, WebClient webClient, String apiKey, String baseUrl) {
        super(vertx);
        this.webClient = webClient;
        this.apiKey = apiKey;
        this.endpoint = baseUrl.endsWith("/chat/completions") ? baseUrl :
            (baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions");
    }

    @Override
    public Future<ModelResponse> chat(List<Message> history, List<ToolDefinition> availableTools, ModelOptions options, AgentSignal signal) {
        JsonObject payload = buildPayload(history, availableTools, options, false);
        if (logger.isDebugEnabled()) {
            logger.debug("[LLM_REQ] Session: {}, Payload: {}", options.modelName(), payload.encode());
        }
        return withSemaphore(
            webClient.postAbs(endpoint)
                .putHeader("Authorization", "Bearer " + apiKey)
                .putHeader("Content-Type", "application/json")
                .as(BodyCodec.jsonObject())
                .sendJsonObject(payload)
                .compose(response -> {
                    if (signal.isAborted()) {
                        return Future.failedFuture(new work.ganglia.kernel.loop.AgentAbortedException());
                    }
                    if (response.statusCode() >= 400) {
                        String errorBody = response.bodyAsString();
                        logger.error("[LLM_ERROR] Status: {}, Body: {}", response.statusCode(), errorBody);
                        return Future.failedFuture(new LLMException(
                            "LLM Error: " + response.statusCode() + " " + response.statusMessage(),
                            null,
                            response.statusCode(),
                            errorBody,
                            null
                        ));
                    }
                    try {
                        return Future.succeededFuture(toModelResponse(response.body()));
                    } catch (Exception e) {
                        return Future.failedFuture(wrapException(e));
                    }
                })
        );
    }

    @Override
    public Future<ModelResponse> chatStream(List<Message> history, List<ToolDefinition> availableTools, ModelOptions options, work.ganglia.port.internal.state.ExecutionContext context, AgentSignal signal) {
        JsonObject payload = buildPayload(history, availableTools, options, true);
        if (logger.isDebugEnabled()) {
            logger.debug("[LLM_REQ] Session: {}, Payload: {}", context.sessionId(), payload.encode());
        }
        Promise<ModelResponse> promise = Promise.promise();

        StringBuilder fullContent = new StringBuilder();
        Map<Integer, ToolCallBuilder> toolCallBuilders = new HashMap<>();
        int[] usage = new int[2]; // [prompt, completion]

        SseParser parser = new SseParser(json -> {
            if (signal.isAborted()) return; // Active cancellation check
            try {
                JsonArray choices = json.getJsonArray("choices");
                if (choices != null && !choices.isEmpty()) {
                    JsonObject choice = choices.getJsonObject(0);
                    JsonObject delta = choice.getJsonObject("delta");
                    if (delta != null) {
                        String content = delta.getString("content");
                        if (content != null && !content.isEmpty()) {
                            fullContent.append(content);
                            publishToken(context, content);
                        }

                        JsonArray toolCalls = delta.getJsonArray("tool_calls");
                        if (toolCalls != null) {
                            for (int i = 0; i < toolCalls.size(); i++) {
                                JsonObject tcDelta = toolCalls.getJsonObject(i);
                                int index = tcDelta.getInteger("index");
                                ToolCallBuilder builder = toolCallBuilders.computeIfAbsent(index, k -> new ToolCallBuilder());

                                String id = tcDelta.getString("id");
                                if (id != null) builder.id = id;

                                JsonObject function = tcDelta.getJsonObject("function");
                                if (function != null) {
                                    String name = function.getString("name");
                                    if (name != null) builder.name = name;

                                    String arguments = function.getString("arguments");
                                    if (arguments != null) builder.arguments.append(arguments);
                                }
                            }
                        }
                    }
                }

                JsonObject usageObj = json.getJsonObject("usage");
                if (usageObj != null) {
                    usage[0] = usageObj.getInteger("prompt_tokens", usage[0]);
                    usage[1] = usageObj.getInteger("completion_tokens", usage[1]);
                }
            } catch (Exception e) {
                logger.error("Failed to parse SSE JSON chunk", e);
            }
        }, promise::fail);

        SseWriteStream writeStream = new SseWriteStream(parser);
        writeStream.exceptionHandler(promise::fail);

        // Active cancellation: fail the promise immediately when abort signal is received
        signal.onAbort(() -> {
            promise.tryFail(new work.ganglia.kernel.loop.AgentAbortedException());
        });

        withSemaphore(
            webClient.postAbs(endpoint)
                .putHeader("Authorization", "Bearer " + apiKey)
                .putHeader("Content-Type", "application/json")
                .putHeader("Accept", "text/event-stream")
                .as(BodyCodec.pipe(writeStream, true))
                .sendJsonObject(payload)
                .onSuccess(response -> {
                    if (signal.isAborted()) {
                        promise.tryFail(new work.ganglia.kernel.loop.AgentAbortedException());
                        return;
                    }
                    if (response.statusCode() >= 400) {
                        String errorBody = response.bodyAsString();
                        logger.error("[LLM_ERROR] Status: {}, Body: {}", response.statusCode(), errorBody);
                        promise.fail(new LLMException("LLM Error: " + response.statusCode() + " " + response.statusMessage(), null, response.statusCode(), errorBody, null));
                    } else {
                        try {
                            List<ToolCall> toolCalls = toolCallBuilders.values().stream()
                                .map(ToolCallBuilder::build)
                                .collect(Collectors.toList());
                            promise.complete(new ModelResponse(fullContent.toString(), toolCalls, new TokenUsage(usage[0], usage[1])));
                        } catch (Exception e) {
                            promise.fail(wrapException(e));
                        }
                    }
                })
                .onFailure(err -> {
                    if (signal.isAborted()) {
                        promise.tryFail(new work.ganglia.kernel.loop.AgentAbortedException());
                    } else {
                        promise.fail(err);
                    }
                })
        );
        return promise.future();
    }

    private JsonObject buildPayload(List<Message> history, List<ToolDefinition> availableTools, ModelOptions options, boolean stream) {
        JsonObject payload = new JsonObject();
        payload.put("model", options.modelName());
        payload.put("temperature", options.temperature());
        if (options.maxTokens() > 0) {
            payload.put("max_tokens", options.maxTokens());
        }
        if (stream) {
            payload.put("stream", true);
            // Some models support stream_options for usage, but since Moonshot and others might fail with it,
            // we will not include it by default to ensure maximum compatibility. Usage can be calculated manually if needed.
        }

        JsonArray messages = new JsonArray();
        for (Message msg : history) {
            messages.add(convertMessage(msg));
        }
        payload.put("messages", messages);

        if (availableTools != null && !availableTools.isEmpty()) {
            JsonArray tools = new JsonArray();
            for (ToolDefinition tool : availableTools) {
                tools.add(convertTool(tool));
            }
            payload.put("tools", tools);
        }

        return payload;
    }

    private JsonObject convertMessage(Message msg) {
        JsonObject obj = new JsonObject();
        switch (msg.role()) {
            case SYSTEM:
                obj.put("role", "system");
                obj.put("content", msg.content() != null ? msg.content() : "");
                break;
            case USER:
                obj.put("role", "user");
                obj.put("content", msg.content() != null ? msg.content() : "");
                break;
            case ASSISTANT:
                obj.put("role", "assistant");
                // Assistant content can be null if there are tool calls, but some APIs prefer empty string
                obj.put("content", msg.content() != null ? msg.content() : "");
                if (msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
                    JsonArray toolCalls = new JsonArray();
                    for (ToolCall tc : msg.toolCalls()) {
                        toolCalls.add(new JsonObject()
                            .put("id", tc.id())
                            .put("type", "function")
                            .put("function", new JsonObject()
                                .put("name", tc.toolName())
                                .put("arguments", new JsonObject(tc.arguments()).encode())
                            )
                        );
                    }
                    obj.put("tool_calls", toolCalls);
                }
                break;
            case TOOL:
                obj.put("role", "tool");
                obj.put("tool_call_id", msg.toolObservation().toolCallId());
                obj.put("content", msg.content() != null ? msg.content() : "");
                break;
        }
        return obj;
    }

    private JsonObject convertTool(ToolDefinition tool) {
        JsonObject obj = new JsonObject();
        obj.put("type", "function");
        JsonObject function = new JsonObject();
        function.put("name", tool.name());
        if (tool.description() != null) {
            function.put("description", tool.description());
        }
        if (tool.jsonSchema() != null && !tool.jsonSchema().isEmpty()) {
            function.put("parameters", new JsonObject(tool.jsonSchema()));
        }
        obj.put("function", function);
        return obj;
    }

    private ModelResponse toModelResponse(JsonObject json) {
        JsonArray choices = json.getJsonArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new LLMException("No choices returned from OpenAI");
        }

        JsonObject choice = choices.getJsonObject(0);
        JsonObject message = choice.getJsonObject("message");

        String content = message.getString("content");
        if (content == null) content = "";

        List<ToolCall> toolCalls = new ArrayList<>();
        JsonArray tcs = message.getJsonArray("tool_calls");
        if (tcs != null) {
            for (int i = 0; i < tcs.size(); i++) {
                JsonObject tc = tcs.getJsonObject(i);
                if ("function".equals(tc.getString("type"))) {
                    JsonObject function = tc.getJsonObject("function");
                    String id = tc.getString("id");
                    if (id == null) id = "call_" + UUID.randomUUID().toString().substring(0, 8);

                    Map<String, Object> args = parseJson(function.getString("arguments"));
                    toolCalls.add(new ToolCall(id, function.getString("name"), args));
                }
            }
        }

        int promptTokens = 0;
        int completionTokens = 0;
        JsonObject usageObj = json.getJsonObject("usage");
        if (usageObj != null) {
            promptTokens = usageObj.getInteger("prompt_tokens", 0);
            completionTokens = usageObj.getInteger("completion_tokens", 0);
        }

        return new ModelResponse(content, toolCalls, new TokenUsage(promptTokens, completionTokens));
    }

    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isEmpty()) return Collections.emptyMap();
        try {
            String sanitized = JsonSanitizer.sanitize(json);
            return new JsonObject(sanitized).getMap();
        } catch (Exception e) {
            logger.error("Failed to decode arguments: {}", json, e);
            return Collections.emptyMap();
        }
    }

    private Throwable wrapException(Throwable e) {
        if (e instanceof LLMException) return e;
        return new LLMException(e.getMessage(), null, null, null, e);
    }

    private static class ToolCallBuilder {
        String id;
        String name;
        StringBuilder arguments = new StringBuilder();

        ToolCall build() {
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
            return new ToolCall(id != null ? id : "call_" + UUID.randomUUID().toString().substring(0, 8), name, argsMap);
        }
    }
}
