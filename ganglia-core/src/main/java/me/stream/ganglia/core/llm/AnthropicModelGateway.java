package me.stream.ganglia.core.llm;

import com.anthropic.client.AnthropicClientAsync;
import com.anthropic.helpers.MessageAccumulator;
import com.anthropic.models.messages.*;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import me.stream.ganglia.core.model.*;
import me.stream.ganglia.core.model.Message;
import me.stream.ganglia.tools.model.ToolCall;
import me.stream.ganglia.tools.model.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class AnthropicModelGateway extends AbstractModelGateway {
    private static final Logger logger = LoggerFactory.getLogger(AnthropicModelGateway.class);
    private final AnthropicClientAsync client;
    private final Supplier<MessageAccumulator> accumulatorSupplier;

    public AnthropicModelGateway(Vertx vertx, AnthropicClientAsync client) {
        this(vertx, client, MessageAccumulator::create);
    }

    AnthropicModelGateway(Vertx vertx, AnthropicClientAsync client, Supplier<MessageAccumulator> accumulatorSupplier) {
        super(vertx);
        this.client = client;
        this.accumulatorSupplier = accumulatorSupplier;
    }

    @Override
    public Future<ModelResponse> chat(List<Message> history, List<ToolDefinition> availableTools, ModelOptions options) {
        MessageCreateParams params = buildParams(history, availableTools, options);
        return Future.fromCompletionStage(client.messages().create(params))
            .map(this::toModelResponse)
            .recover(err -> Future.failedFuture(wrapException(err)));
    }

    @Override
    public Future<ModelResponse> chatStream(List<Message> history, List<ToolDefinition> availableTools, ModelOptions options, String sessionId) {
        MessageCreateParams params = buildParams(history, availableTools, options);
        Promise<ModelResponse> promise = Promise.promise();
        MessageAccumulator accumulator = accumulatorSupplier.get();
        String observationAddress = "ganglia.observations." + sessionId;

        client.messages().createStreaming(params).subscribe(new com.anthropic.core.http.AsyncStreamResponse.Handler<RawMessageStreamEvent>() {
            @Override
            public void onNext(RawMessageStreamEvent event) {
                accumulator.accumulate(event);

                if (event.isContentBlockDelta()) {
                    RawContentBlockDelta delta = event.asContentBlockDelta().delta();
                    if (delta.isText()) {
                        publishToken(sessionId, delta.asText().text());
                    }
                }
            }

            @Override
            public void onComplete(Optional<Throwable> throwable) {
                if (throwable.isPresent()) {
                    promise.fail(wrapException(throwable.get()));
                } else {
                    try {
                        promise.complete(toModelResponse(accumulator.message()));
                    } catch (Exception e) {
                        promise.fail(wrapException(e));
                    }
                }
            }
        });

        return promise.future();
    }

    private MessageCreateParams buildParams(List<Message> history, List<ToolDefinition> availableTools, ModelOptions options) {
        MessageCreateParams.Builder builder = MessageCreateParams.builder();
        builder.model(options.modelName());
        builder.maxTokens(options.maxTokens());
        builder.temperature(options.temperature());

        // Extract system message if present
        String systemMessage = mergeSystemMessages(history);
        if (systemMessage != null) {
            builder.system(systemMessage);
        }

        // Map messages
        List<MessageParam> messages = history.stream()
            .filter(m -> m.role() != Role.SYSTEM)
            .map(this::convertMessage)
            .collect(Collectors.toList());

        builder.messages(messages);

        // Map tools
        if (availableTools != null && !availableTools.isEmpty()) {
            builder.tools(availableTools.stream()
                .map(this::convertTool)
                .map(ToolUnion::ofTool)
                .collect(Collectors.toList()));
        }

        return builder.build();
    }

    private MessageParam convertMessage(Message msg) {
        MessageParam.Builder builder = MessageParam.builder();
        switch (msg.role()) {
            case USER:
                builder.role(MessageParam.Role.USER);
                break;
            case ASSISTANT:
                builder.role(MessageParam.Role.ASSISTANT);
                break;
            case TOOL:
                builder.role(MessageParam.Role.USER);
                break;
            default:
                throw new IllegalArgumentException("Unsupported role for Anthropic messages: " + msg.role());
        }

        List<ContentBlockParam> content = new ArrayList<>();
        if (msg.role() == Role.TOOL) {
            content.add(ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                    .toolUseId(msg.toolCallId())
                    .content(msg.content())
                    .build()));
        } else {
            if (msg.content() != null && !msg.content().isEmpty()) {
                content.add(ContentBlockParam.ofText(TextBlockParam.builder().text(msg.content()).build()));
            }
            if (msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
                for (ToolCall tc : msg.toolCalls()) {
                    content.add(ContentBlockParam.ofToolUse(ToolUseBlockParam.builder()
                            .id(tc.id())
                            .name(tc.toolName())
                            .input(com.anthropic.core.JsonValue.from(tc.arguments()))
                            .build()));
                }
            }
        }

        builder.contentOfBlockParams(content);
        return builder.build();
    }

    private com.anthropic.models.messages.Tool convertTool(ToolDefinition tool) {
        Tool.Builder builder = com.anthropic.models.messages.Tool.builder()
                .name(tool.name())
                .description(tool.description());

        if (tool.jsonSchema() != null && !tool.jsonSchema().isEmpty()) {
            Map<String, Object> schemaMap = Json.decodeValue(tool.jsonSchema(), Map.class);
            Tool.InputSchema.Builder schemaBuilder = Tool.InputSchema.builder();
            schemaMap.forEach((k, v) -> schemaBuilder.putAdditionalProperty(k, com.anthropic.core.JsonValue.from(v)));
            builder.inputSchema(schemaBuilder.build());
        } else {
            builder.inputSchema(Tool.InputSchema.builder().build());
        }

        return builder.build();
    }

    private ModelResponse toModelResponse(com.anthropic.models.messages.Message message) {
        String content = message.content().stream()
            .filter(ContentBlock::isText)
            .map(ContentBlock::asText)
            .map(TextBlock::text)
            .collect(Collectors.joining("\n"));

        List<ToolCall> toolCalls = message.content().stream()
            .filter(ContentBlock::isToolUse)
            .map(ContentBlock::asToolUse)
            .map(tu -> {
                Map<String, Object> args = tu._input().convert(Map.class);
                return new ToolCall(tu.id(), tu.name(), args);
            })
            .collect(Collectors.toList());

        TokenUsage usage = new TokenUsage(
            (int) message.usage().inputTokens(),
            (int) message.usage().outputTokens()
        );

        return new ModelResponse(content, toolCalls, usage);
    }

    private Throwable wrapException(Throwable throwable) {
        if (throwable instanceof com.anthropic.errors.AnthropicServiceException) {
            com.anthropic.errors.AnthropicServiceException e = (com.anthropic.errors.AnthropicServiceException) throwable;
            String errorCode = null;
            try {
                // Try to extract code from body if possible
                Map<String, Object> body = e.body().convert(Map.class);
                if (body.containsKey("error") && body.get("error") instanceof Map) {
                    Map<String, Object> error = (Map<String, Object>) body.get("error");
                    if (error.containsKey("type")) errorCode = String.valueOf(error.get("type"));
                }
            } catch (Exception ex) {
                // ignore
            }
            return new LLMException(
                e.getMessage(),
                errorCode,
                e.statusCode(),
                null,
                e
            );
        } else if (throwable instanceof com.anthropic.errors.AnthropicException) {
            return new LLMException(throwable.getMessage(), null, null, null, throwable);
        }
        return throwable;
    }
}
