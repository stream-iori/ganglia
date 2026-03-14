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
import work.ganglia.port.external.llm.ChatRequest;
import work.ganglia.port.external.llm.ModelOptions;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.chat.Message;
import work.ganglia.port.internal.state.AgentSignal;
import work.ganglia.port.internal.state.TokenUsage;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.external.tool.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.ganglia.port.external.llm.ModelGateway;

import java.util.*;
import java.util.stream.Collectors;

public class AnthropicModelGateway extends AbstractModelGateway {
    private static final Logger logger = LoggerFactory.getLogger(AnthropicModelGateway.class);
    private final WebClient webClient;
    private final String apiKey;
    private final String endpoint;
    private final int timeoutMs;

    public AnthropicModelGateway(Vertx vertx, WebClient webClient, String apiKey, String baseUrl) {
        this(vertx, webClient, apiKey, baseUrl, 60000);
    }

    public AnthropicModelGateway(Vertx vertx, WebClient webClient, String apiKey, String baseUrl, int timeoutMs) {
        super(vertx);
        this.webClient = webClient;
        this.apiKey = apiKey;
        this.timeoutMs = timeoutMs;
        this.endpoint = baseUrl.endsWith("/v1/messages") ? baseUrl :
            (baseUrl.endsWith("/") ? baseUrl + "v1/messages" : baseUrl + "/v1/messages");
    }

    @Override
    public Future<ModelResponse> chat(ChatRequest request) {
        JsonObject payload = buildPayload(request.messages(), request.tools(), request.options(), false);
        return withSemaphore(
            webClient.postAbs(endpoint)
                .putHeader("x-api-key", apiKey)
                .putHeader("anthropic-version", "2023-06-01")
                .putHeader("Content-Type", "application/json")
                .timeout(timeoutMs)
                .as(BodyCodec.jsonObject())

                .sendJsonObject(payload)
                .compose(response -> {
                    if (request.signal().isAborted()) {
                        return Future.failedFuture(new work.ganglia.kernel.loop.AgentAbortedException());
                    }
                    if (response.statusCode() >= 400) {
                        return Future.failedFuture(new LLMException(
                            "Anthropic Error: " + response.statusMessage(),
                            null,
                            response.statusCode(),
                            response.bodyAsJsonObject() != null ? response.bodyAsJsonObject().encode() : null,
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
    public Future<ModelResponse> chatStream(ChatRequest request, work.ganglia.port.internal.state.ExecutionContext context) {
        JsonObject payload = buildPayload(request.messages(), request.tools(), request.options(), true);
        Promise<ModelResponse> promise = Promise.promise();

        StringBuilder fullContent = new StringBuilder();
        Map<Integer, ToolCallBuilder> toolCallBuilders = new HashMap<>();
        int[] usage = new int[2]; // [input, output]

        SseParser parser = new SseParser(json -> {
            if (request.signal().isAborted()) return; // Active cancellation check
            try {
                String type = json.getString("type");
                if (type == null) return;

                switch (type) {
                    case "message_start":
                        JsonObject message = json.getJsonObject("message");
                        if (message != null && message.getJsonObject("usage") != null) {
                            usage[0] = message.getJsonObject("usage").getInteger("input_tokens", 0);
                        }
                        break;
                    case "content_block_start":
                        JsonObject block = json.getJsonObject("content_block");
                        if (block != null && "tool_use".equals(block.getString("type"))) {
                            int index = json.getInteger("index");
                            ToolCallBuilder builder = toolCallBuilders.computeIfAbsent(index, k -> new ToolCallBuilder());
                            builder.id = block.getString("id");
                            builder.name = block.getString("name");
                        }
                        break;
                    case "content_block_delta":
                        JsonObject delta = json.getJsonObject("delta");
                        if (delta != null) {
                            String deltaType = delta.getString("type");
                            if ("text_delta".equals(deltaType)) {
                                String text = delta.getString("text");
                                if (text != null && !text.isEmpty()) {
                                    fullContent.append(text);
                                    publishToken(context, text);
                                }
                            } else if ("input_json_delta".equals(deltaType)) {
                                int index = json.getInteger("index");
                                ToolCallBuilder builder = toolCallBuilders.get(index);
                                if (builder != null) {
                                    String partialJson = delta.getString("partial_json");
                                    if (partialJson != null) {
                                        builder.arguments.append(partialJson);
                                    }
                                }
                            }
                        }
                        break;
                    case "message_delta":
                        JsonObject msgDelta = json.getJsonObject("delta");
                        JsonObject msgUsage = json.getJsonObject("usage");
                        if (msgUsage != null) {
                            usage[1] = msgUsage.getInteger("output_tokens", 0);
                        }
                        break;
                }
            } catch (Exception e) {
                logger.error("Failed to parse Anthropic SSE chunk", e);
            }
        }, promise::tryFail);

        SseWriteStream writeStream = new SseWriteStream(parser);
        writeStream.exceptionHandler(promise::tryFail);

        // Active cancellation: fail the promise immediately when abort signal is received
        request.signal().onAbort(() -> {
            promise.tryFail(new work.ganglia.kernel.loop.AgentAbortedException());
        });

        withSemaphore(
            webClient.postAbs(endpoint)
                .putHeader("x-api-key", apiKey)
                .putHeader("anthropic-version", "2023-06-01")
                .putHeader("Content-Type", "application/json")
                .putHeader("Accept", "text/event-stream")
                .timeout(timeoutMs)
                .as(BodyCodec.pipe(writeStream, true))
                .sendJsonObject(payload)
                .onSuccess(response -> {
                    if (request.signal().isAborted()) {
                        promise.tryFail(new work.ganglia.kernel.loop.AgentAbortedException());
                        return;
                    }
                    if (response.statusCode() >= 400) {
                        promise.fail(new LLMException("Anthropic Error: " + response.statusCode(), null, response.statusCode(), null, null));
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
                    if (request.signal().isAborted()) {
                        promise.tryFail(new work.ganglia.kernel.loop.AgentAbortedException());
                    } else {
                        promise.tryFail(err);
                    }
                })
        );
        return promise.future();
    }


    private JsonObject buildPayload(List<Message> history, List<ToolDefinition> availableTools, ModelOptions options, boolean stream) {
        JsonObject payload = new JsonObject();
        payload.put("model", options.modelName());
        payload.put("max_tokens", options.maxTokens());
        payload.put("temperature", options.temperature());
        if (stream) {
            payload.put("stream", true);
        }

        String systemPrompt = mergeSystemMessages(history);
        if (systemPrompt != null) {
            payload.put("system", systemPrompt);
        }

        JsonArray messages = new JsonArray();
        for (Message msg : history) {
            if (msg.role() != work.ganglia.port.chat.Role.SYSTEM) {
                messages.add(convertMessage(msg));
            }
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
            case USER:
                obj.put("role", "user");
                obj.put("content", new JsonArray().add(new JsonObject().put("type", "text").put("text", msg.content())));
                break;
            case ASSISTANT:
                obj.put("role", "assistant");
                JsonArray contentArr = new JsonArray();
                if (msg.content() != null && !msg.content().isEmpty()) {
                    contentArr.add(new JsonObject().put("type", "text").put("text", msg.content()));
                }
                if (msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
                    for (ToolCall tc : msg.toolCalls()) {
                        contentArr.add(new JsonObject()
                            .put("type", "tool_use")
                            .put("id", tc.id())
                            .put("name", tc.toolName())
                            .put("input", new JsonObject(tc.arguments()))
                        );
                    }
                }
                obj.put("content", contentArr);
                break;
            case TOOL:
                obj.put("role", "user");
                JsonArray toolResArr = new JsonArray();
                toolResArr.add(new JsonObject()
                    .put("type", "tool_result")
                    .put("tool_use_id", msg.toolObservation().toolCallId())
                    .put("content", msg.content() != null ? msg.content() : "")
                );
                obj.put("content", toolResArr);
                break;
            default:
                throw new IllegalArgumentException("Unsupported role for Anthropic messages: " + msg.role());
        }
        return obj;
    }

    private JsonObject convertTool(ToolDefinition tool) {
        JsonObject obj = new JsonObject();
        obj.put("name", tool.name());
        if (tool.description() != null) {
            obj.put("description", tool.description());
        }
        if (tool.jsonSchema() != null && !tool.jsonSchema().isEmpty()) {
            obj.put("input_schema", new JsonObject(tool.jsonSchema()));
        } else {
            obj.put("input_schema", new JsonObject().put("type", "object").put("properties", new JsonObject()));
        }
        return obj;
    }

    private ModelResponse toModelResponse(JsonObject json) {
        JsonArray content = json.getJsonArray("content");
        if (content == null) {
            throw new LLMException("No content returned from Anthropic");
        }

        StringBuilder text = new StringBuilder();
        List<ToolCall> toolCalls = new ArrayList<>();

        for (int i = 0; i < content.size(); i++) {
            JsonObject block = content.getJsonObject(i);
            String type = block.getString("type");
            if ("text".equals(type)) {
                text.append(block.getString("text"));
            } else if ("tool_use".equals(type)) {
                String id = block.getString("id");
                String name = block.getString("name");
                JsonObject input = block.getJsonObject("input");
                toolCalls.add(new ToolCall(id, name, input != null ? input.getMap() : Collections.emptyMap()));
            }
        }

        int inputTokens = 0;
        int outputTokens = 0;
        JsonObject usageObj = json.getJsonObject("usage");
        if (usageObj != null) {
            inputTokens = usageObj.getInteger("input_tokens", 0);
            outputTokens = usageObj.getInteger("output_tokens", 0);
        }

        return new ModelResponse(text.toString(), toolCalls, new TokenUsage(inputTokens, outputTokens));
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
            return new ToolCall(id != null ? id : UUID.randomUUID().toString(), name, argsMap);
        }
    }
}
