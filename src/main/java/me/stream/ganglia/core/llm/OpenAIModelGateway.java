package me.stream.ganglia.core.llm;

import com.openai.client.OpenAIClientAsync;
import com.openai.client.okhttp.OpenAIOkHttpClientAsync;
import com.openai.core.http.AsyncStreamResponse;
import com.openai.helpers.ChatCompletionAccumulator;
import com.openai.models.ChatModel;
import com.openai.models.FunctionDefinition;
import com.openai.models.chat.completions.*;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.impl.NoStackTraceThrowable;
import me.stream.ganglia.core.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class OpenAIModelGateway implements ModelGateway {
    private static final Logger logger = LoggerFactory.getLogger(OpenAIModelGateway.class);
    private final OpenAIClientAsync client;
    private final Vertx vertx;

    public OpenAIModelGateway(Vertx vertx, String apiKey, String baseUrl) {
        this.vertx = vertx;
        this.client = OpenAIOkHttpClientAsync.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build();
    }

    @Override
    public Future<ModelResponse> chat(List<Message> history, List<ToolDefinition> availableTools, ModelOptions options) {
        ChatCompletionCreateParams params = buildParams(history, availableTools, options);
        
        return Future.fromCompletionStage(client.chat().completions().create(params))
                .map(this::toModelResponse);
    }

    @Override
    public Future<ModelResponse> chatStream(List<Message> history, List<ToolDefinition> availableTools, ModelOptions options, String streamAddress) {
        ChatCompletionCreateParams params = buildParams(history, availableTools, options);
        Promise<ModelResponse> promise = Promise.promise();
        
        // Use OpenAI SDK Accumulator
        ChatCompletionAccumulator accumulator = ChatCompletionAccumulator.create();

        AsyncStreamResponse<ChatCompletionChunk> stream = client.chat().completions().createStreaming(params);
        stream.subscribe(new AsyncStreamResponse.Handler<ChatCompletionChunk>() {
            @Override
            public void onNext(ChatCompletionChunk chunk) {
                // Accumulate state
                accumulator.accumulate(chunk);

                // Publish content delta to EventBus
                for (ChatCompletionChunk.Choice choice : chunk.choices()) {
                    choice.delta().content().ifPresent(content -> {
                        if (!content.isEmpty()) {
                            vertx.eventBus().publish(streamAddress, content);
                        }
                    });
                }
            }

            @Override
            public void onComplete(Optional<Throwable> throwable) {
                if (throwable.isPresent()) {
                    promise.fail(throwable.get());
                } else {
                    try {
                        ChatCompletion completion = accumulator.chatCompletion();
                        promise.complete(toModelResponse(completion));
                    } catch (Exception e) {
                        promise.fail(e);
                    }
                }
            }
        });

        return promise.future();
    }

    private ModelResponse toModelResponse(ChatCompletion completion) {
        if (completion.choices().isEmpty()) {
            throw new NoStackTraceThrowable("No choices returned from OpenAI");
        }

        ChatCompletion.Choice choice = completion.choices().get(0);
        String content = choice.message().content().orElse("");
        
        List<ToolCall> toolCalls = convertToolCalls(choice.message().toolCalls());
        
        // Extract usage
        int promptTokens = completion.usage().map(u -> (int)u.promptTokens()).orElse(0);
        int completionTokens = completion.usage().map(u -> (int)u.completionTokens()).orElse(0);
        
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
                if (msg.content() != null && !msg.content().isEmpty()) {
                    builder.content(ChatCompletionAssistantMessageParam.Content.ofText(msg.content()));
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
                                .toolCallId(msg.toolCallId())
                                .content(msg.content())
                                .build()
                );
            default:
                throw new IllegalArgumentException("Unknown role: " + msg.role());
        }
    }

    private ChatCompletionTool convertTool(ToolDefinition tool) {
        ChatCompletionFunctionTool functionTool = ChatCompletionFunctionTool.builder()
                .function(FunctionDefinition.builder()
                        .name(tool.name())
                        .description(tool.description())
                        // .parameters(...)
                        .build())
                .build();
        
        return ChatCompletionTool.ofFunction(functionTool);
    }

    private List<ToolCall> convertToolCalls(Optional<List<ChatCompletionMessageToolCall>> openAIToolCalls) {
        if (openAIToolCalls.isEmpty()) {
            return Collections.emptyList();
        }
        return openAIToolCalls.get().stream()
                .filter(ChatCompletionMessageToolCall::isFunction)
                .map(tc -> {
                    ChatCompletionMessageFunctionToolCall ftc = tc.asFunction();
                    return new ToolCall(
                        ftc.id(),
                        ftc.function().name(),
                        parseJson(ftc.function().arguments())
                    );
                })
                .collect(Collectors.toList());
    }

    private String toJsonString(Map<String, Object> map) {
        try {
            return io.vertx.core.json.Json.encode(map);
        } catch (Exception e) {
            logger.error("Failed to encode arguments", e);
            return "{}";
        }
    }

    private Map<String, Object> parseJson(String json) {
        try {
            return io.vertx.core.json.Json.decodeValue(json, Map.class);
        } catch (Exception e) {
            logger.error("Failed to decode arguments", e);
            return Collections.emptyMap();
        }
    }
}
