package work.ganglia.core.llm;

import com.openai.client.OpenAIClientAsync;
import com.openai.client.okhttp.OpenAIOkHttpClientAsync;
import com.openai.core.http.AsyncStreamResponse;
import com.openai.helpers.ChatCompletionAccumulator;
import com.openai.models.ChatModel;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.chat.completions.*;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import work.ganglia.core.llm.util.JsonSanitizer;
import work.ganglia.core.model.Message;
import work.ganglia.core.model.ModelOptions;
import work.ganglia.core.model.ModelResponse;
import work.ganglia.core.model.TokenUsage;
import work.ganglia.tools.model.ToolCall;
import work.ganglia.tools.model.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class OpenAIModelGateway extends AbstractModelGateway {
    private static final Logger logger = LoggerFactory.getLogger(OpenAIModelGateway.class);
    private final OpenAIClientAsync client;

    public OpenAIModelGateway(Vertx vertx, String apiKey, String baseUrl) {
        super(vertx);
        this.client = OpenAIOkHttpClientAsync.builder()
            .apiKey(apiKey)
            .baseUrl(baseUrl)
            .build();
    }

    @Override
    public Future<ModelResponse> chat(List<Message> history, List<ToolDefinition> availableTools, ModelOptions options) {
        ChatCompletionCreateParams params = buildParams(history, availableTools, options);
        logger.debug("[LLM_REQUEST] Model: {}, History Size: {}, Tools: {}",
            options.modelName(), history.size(), availableTools != null ? availableTools.size() : 0);
        traceParams(params);
        return withSemaphore(Future.fromCompletionStage(client.chat().completions().create(params))
            .map(this::toModelResponse)
            .recover(err -> Future.failedFuture(wrapException(err))));
    }

    private Throwable wrapException(Throwable throwable) {
        if (throwable instanceof com.openai.errors.OpenAIServiceException) {
            com.openai.errors.OpenAIServiceException e = (com.openai.errors.OpenAIServiceException) throwable;
            return new LLMException(
                e.getMessage(),
                e.code().orElse(null),
                e.statusCode(),
                null,
                e
            );
        } else if (throwable instanceof com.openai.errors.OpenAIException) {
            return new LLMException(throwable.getMessage(), null, null, null, throwable);
        }
        return throwable;
    }

    @Override
    public Future<ModelResponse> chatStream(List<Message> history, List<ToolDefinition> availableTools, ModelOptions options, String sessionId) {
        ChatCompletionCreateParams params = buildParams(history, availableTools, options);
        logger.debug("[LLM_STREAM_START] Model: {}, History Size: {}, Tools: {}",
            options.modelName(), history.size(), availableTools != null ? availableTools.size() : 0);
        traceParams(params);

        return withSemaphore(doChatStream(params, sessionId));
    }

    private Future<ModelResponse> doChatStream(ChatCompletionCreateParams params, String sessionId) {
        Promise<ModelResponse> promise = Promise.promise();
        // Use OpenAI SDK Accumulator
        ChatCompletionAccumulator accumulator = ChatCompletionAccumulator.create();

        AsyncStreamResponse<ChatCompletionChunk> stream = client.chat().completions().createStreaming(params);
        stream.subscribe(new AsyncStreamResponse.Handler<>() {
            @Override
            public void onNext(ChatCompletionChunk chunk) {
                // Accumulate state
                accumulator.accumulate(chunk);

                // Publish content delta to EventBus as ObservationEvent
                for (ChatCompletionChunk.Choice choice : chunk.choices()) {
                    choice.delta().content().ifPresent(content -> {
                        publishToken(sessionId, content);
                    });
                }
            }

            @Override
            public void onComplete(Optional<Throwable> throwable) {
                if (throwable.isPresent()) {
                    promise.fail(wrapException(throwable.get()));
                } else {
                    try {
                        ChatCompletion completion = accumulator.chatCompletion();
                        promise.complete(toModelResponse(completion));
                    } catch (Exception e) {
                        promise.fail(wrapException(e));
                    }
                }
            }
        });

        return promise.future();
    }

    private void traceParams(ChatCompletionCreateParams params) {
        if (logger.isTraceEnabled()) {
            logger.trace("[LLM_REQUEST_DATA] Params: {}", Json.encode(params));
        }
    }

    private ModelResponse toModelResponse(ChatCompletion completion) {
        if (completion.choices().isEmpty()) {
            logger.error("Model returned an empty choice list.");
            throw new LLMException("No choices returned from OpenAI");
        }

        ChatCompletion.Choice choice = completion.choices().get(0);
        String content = choice.message().content().orElse("");

        List<ToolCall> toolCalls = convertToolCalls(choice.message().toolCalls());

        // Extract usage
        int promptTokens = completion.usage().map(u -> (int) u.promptTokens()).orElse(0);
        int completionTokens = completion.usage().map(u -> (int) u.completionTokens()).orElse(0);

        logger.debug("[LLM_RESPONSE] Content: {} chars, ToolCalls: {}, Usage: [P: {}, C: {}]",
            content.length(), toolCalls.size(), promptTokens, completionTokens);
        if (content.length() > 0) {
            logger.debug("[LLM_RESPONSE_CONTENT] {}", content);
        }
        if (!toolCalls.isEmpty()) {
            logger.debug("[LLM_RESPONSE_TOOLS] {}", toolCalls);
        }

        return new ModelResponse(content, toolCalls, new TokenUsage(promptTokens, completionTokens));
    }

    private ChatCompletionCreateParams buildParams(List<Message> history, List<ToolDefinition> availableTools, ModelOptions options) {
        List<ChatCompletionMessageParam> messages = history.stream()
            .map(this::convertMessage)
            .collect(Collectors.toList());

        ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder()
            .messages(messages)
            .model(ChatModel.of(options.modelName()))
            .temperature(options.temperature())
            .maxTokens(options.maxTokens());

        if (availableTools != null && !availableTools.isEmpty()) {
            List<ChatCompletionTool> tools = availableTools.stream()
                .map(this::convertTool)
                .collect(Collectors.toList());
            builder.tools(tools);
        }

        return builder.build();
    }

    private ChatCompletionMessageParam convertMessage(Message msg) {
        switch (msg.role()) {
            case SYSTEM:
                return ChatCompletionMessageParam.ofSystem(
                    ChatCompletionSystemMessageParam.builder().content(msg.content()).build()
                );
            case USER:
                return ChatCompletionMessageParam.ofUser(
                    ChatCompletionUserMessageParam.builder().content(msg.content()).build()
                );
            case ASSISTANT:
                ChatCompletionAssistantMessageParam.Builder builder = ChatCompletionAssistantMessageParam.builder();
                // Some providers (like Moonshot) require content to be an empty string if tool calls are present
                if (msg.content() != null && !msg.content().isEmpty()) {
                    builder.content(ChatCompletionAssistantMessageParam.Content.ofText(msg.content()));
                } else if (msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
                    builder.content(ChatCompletionAssistantMessageParam.Content.ofText(""));
                }
                
                if (msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
                    List<ChatCompletionMessageToolCall> toolCalls = msg.toolCalls().stream()
                        .map(tc -> ChatCompletionMessageToolCall.ofFunction(
                            ChatCompletionMessageFunctionToolCall.builder()
                                .id(tc.id())
                                .function(ChatCompletionMessageFunctionToolCall.Function.builder()
                                    .name(tc.toolName())
                                    .arguments(toJsonString(tc.arguments()))
                                    .build())
                                .build()
                        ))
                        .collect(Collectors.toList());
                    builder.toolCalls(toolCalls);
                }
                return ChatCompletionMessageParam.ofAssistant(builder.build());
            case TOOL:
                return ChatCompletionMessageParam.ofTool(
                    ChatCompletionToolMessageParam.builder()
                        .toolCallId(msg.toolObservation().toolCallId())
                        .content(msg.content() != null ? msg.content() : "")
                        .build()
                );
            default:
                throw new IllegalArgumentException("Unknown role: " + msg.role());
        }
    }

    private ChatCompletionTool convertTool(ToolDefinition tool) {
        FunctionDefinition.Builder functionBuilder = FunctionDefinition.builder()
            .name(tool.name())
            .description(tool.description());

        if (tool.jsonSchema() != null && !tool.jsonSchema().isEmpty()) {
            Map<String, Object> schemaMap = parseJson(tool.jsonSchema());
            FunctionParameters.Builder paramsBuilder = FunctionParameters.builder();
            schemaMap.forEach((key, value) -> {
                paramsBuilder.putAdditionalProperty(key, com.openai.core.JsonValue.from(value));
            });
            functionBuilder.parameters(paramsBuilder.build());
        }

        ChatCompletionFunctionTool functionTool = ChatCompletionFunctionTool.builder()
            .function(functionBuilder.build())
            .build();

        return ChatCompletionTool.ofFunction(functionTool);
    }

    private List<ToolCall> convertToolCalls(Optional<List<ChatCompletionMessageToolCall>> openAIToolCalls) {
        return openAIToolCalls
            .map(chatCompletionMessageToolCalls -> chatCompletionMessageToolCalls.stream()
                .filter(ChatCompletionMessageToolCall::isFunction)
                .map(tc -> {
                    ChatCompletionMessageFunctionToolCall ftc = tc.asFunction();
                    String id = ftc.id();
                    if (id == null || id.isEmpty()) {
                        id = "call_" + java.util.UUID.randomUUID().toString().substring(0, 8);
                    }
                    return new ToolCall(
                        id,
                        ftc.function().name(),
                        parseJson(ftc.function().arguments())
                    );
                })
                .collect(Collectors.toList()))
            .orElse(Collections.emptyList());
    }

    private String toJsonString(Map<String, Object> map) {
        try {
            return Json.encode(map);
        } catch (Exception e) {
            logger.error("Failed to encode arguments", e);
            return "{}";
        }
    }

    private Map<String, Object> parseJson(String json) {
        try {
            String sanitized = JsonSanitizer.sanitize(json);
            return Json.decodeValue(sanitized, Map.class);
        } catch (Exception e) {
            logger.error("Failed to decode arguments: {}", json, e);
            return Collections.emptyMap();
        }
    }
}
