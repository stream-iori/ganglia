package me.stream.ganglia.core.llm;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.http.StreamResponse;
import com.openai.models.ChatModel;
import com.openai.models.FunctionDefinition;
import com.openai.models.chat.completions.*;
import io.vertx.core.Future;
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
    private final OpenAIClient client;
    private final Vertx vertx;

    public OpenAIModelGateway(Vertx vertx, String apiKey, String baseUrl) {
        this.vertx = vertx;
        this.client = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build();
    }

    @Override
    public Future<ModelResponse> chat(List<Message> history, List<ToolDefinition> availableTools, ModelOptions options) {
        return vertx.executeBlocking(() -> {
            try {
                ChatCompletionCreateParams params = buildParams(history, availableTools, options);
                ChatCompletion completion = client.chat().completions().create(params);
                
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
            } catch (Exception e) {
                logger.error("Error calling OpenAI API", e);
                throw e;
            }
        });
    }

    @Override
    public Future<Void> chatStream(List<Message> history, List<ToolDefinition> availableTools, ModelOptions options, String streamAddress) {
        return vertx.executeBlocking(() -> {
            try {
                ChatCompletionCreateParams params = buildParams(history, availableTools, options);
                try (StreamResponse<ChatCompletionChunk> stream = client.chat().completions().createStreaming(params)) {
                    stream.stream().forEach(chunk -> {
                        if (!chunk.choices().isEmpty()) {
                            ChatCompletionChunk.Choice choice = chunk.choices().get(0);
                            // Handling content chunks
                            choice.delta().content().ifPresent(content -> {
                                if (!content.isEmpty()) {
                                    vertx.eventBus().publish(streamAddress, content);
                                }
                            });
                            // TODO: Handle ToolCall streaming (accumulator) if needed. 
                            // For now, focusing on text streaming.
                        }
                    });
                }
                return null;
            } catch (Exception e) {
                logger.error("Error streaming from OpenAI API", e);
                throw e;
            }
        });
    }

    private ChatCompletionCreateParams buildParams(List<Message> history, List<ToolDefinition> availableTools, ModelOptions options) {
        // Use concrete implementations of ChatCompletionMessageParam
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
                                            // .type(...) omitted
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
                // .type(...) omitted
                .function(FunctionDefinition.builder()
                        .name(tool.name())
                        .description(tool.description())
                        // .parameters(parseJson(tool.jsonSchema())) // TODO: JSON Schema
                        .build())
                .build();
        
        return ChatCompletionTool.ofFunction(functionTool);
    }

    private List<ToolCall> convertToolCalls(Optional<List<ChatCompletionMessageToolCall>> openAIToolCalls) {
        if (openAIToolCalls.isEmpty()) {
            return Collections.emptyList();
        }
        return openAIToolCalls.get().stream()
                .filter(ChatCompletionMessageToolCall::isFunction) // Only handle Function calls
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

    // Helper to serialize Map to JSON String
    private String toJsonString(Map<String, Object> map) {
        try {
            return io.vertx.core.json.Json.encode(map);
        } catch (Exception e) {
            logger.error("Failed to encode arguments", e);
            return "{}";
        }
    }

    // Helper to parse JSON String to Map
    private Map<String, Object> parseJson(String json) {
        try {
            return io.vertx.core.json.Json.decodeValue(json, Map.class);
        } catch (Exception e) {
            logger.error("Failed to decode arguments", e);
            return Collections.emptyMap();
        }
    }
}
