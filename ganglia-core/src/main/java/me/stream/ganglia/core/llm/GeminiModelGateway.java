package me.stream.ganglia.core.llm;

import com.google.genai.Client;
import com.google.genai.types.*;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import me.stream.ganglia.core.model.*;
import me.stream.ganglia.core.model.Message;
import me.stream.ganglia.tools.model.ToolCall;
import me.stream.ganglia.tools.model.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class GeminiModelGateway extends AbstractModelGateway {
    private static final Logger logger = LoggerFactory.getLogger(GeminiModelGateway.class);
    private final Client client;

    public GeminiModelGateway(Vertx vertx, Client client) {
        super(vertx);
        this.client = client;
    }

    @Override
    public Future<ModelResponse> chat(List<Message> history, List<ToolDefinition> availableTools, ModelOptions options) {
        GenerateContentConfig config = buildConfig(history, availableTools, options);
        List<Content> contents = buildContents(history);

        return vertx.executeBlocking(() -> {
            try {
                GenerateContentResponse response = client.models.generateContent(options.modelName(), contents, config);
                return toModelResponse(response);
            } catch (Exception e) {
                throw new RuntimeException(wrapException(e));
            }
        });
    }

    @Override
    public Future<ModelResponse> chatStream(List<Message> history, List<ToolDefinition> availableTools, ModelOptions options, String sessionId) {
        GenerateContentConfig config = buildConfig(history, availableTools, options);
        List<Content> contents = buildContents(history);
        Promise<ModelResponse> promise = Promise.promise();
        String observationAddress = "ganglia.observations." + sessionId;

        vertx.executeBlocking(() -> {
            try {
                Iterable<GenerateContentResponse> stream = client.models.generateContentStream(options.modelName(), contents, config);

                StringBuilder fullContent = new StringBuilder();
                List<ToolCall> allToolCalls = new ArrayList<>();
                int promptTokens = 0;
                int candidateTokens = 0;

                for (GenerateContentResponse response : stream) {
                    // Accumulate tokens for streaming UI
                    String text = response.text();
                    if (text != null) {
                        fullContent.append(text);
                        publishToken(sessionId, text);
                    }

                    // Collect usage info
                    if (response.usageMetadata().isPresent()) {
                        GenerateContentResponseUsageMetadata meta = response.usageMetadata().get();
                        promptTokens = meta.promptTokenCount().orElse(promptTokens);
                        candidateTokens = meta.candidatesTokenCount().orElse(candidateTokens);
                    }

                    // Collect tool calls
                    if (response.candidates().isPresent()) {
                        List<Candidate> candidates = response.candidates().get();
                        if (!candidates.isEmpty()) {
                            Candidate candidate = candidates.get(0);
                            if (candidate.content().isPresent()) {
                                Content candContent = candidate.content().get();
                                if (candContent.parts().isPresent()) {
                                    for (Part part : candContent.parts().get()) {
                                        if (part.functionCall().isPresent()) {
                                            FunctionCall fc = part.functionCall().get();
                                            allToolCalls.add(new ToolCall(
                                                fc.id().orElse(UUID.randomUUID().toString()),
                                                fc.name().orElse("unknown"),
                                                fc.args().orElse(Collections.emptyMap())
                                            ));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                promise.complete(new ModelResponse(
                    fullContent.toString(),
                    allToolCalls,
                    new TokenUsage(promptTokens, candidateTokens)
                ));
            } catch (Exception e) {
                promise.fail(wrapException(e));
            }
            return null;
        });

        return promise.future();
    }

    private GenerateContentConfig buildConfig(List<Message> history, List<ToolDefinition> availableTools, ModelOptions options) {
        GenerateContentConfig.Builder builder = GenerateContentConfig.builder();
        builder.temperature((float) options.temperature());
        builder.maxOutputTokens(options.maxTokens());

        // System instructions
        String systemMessage = mergeSystemMessages(history);
        if (systemMessage != null) {
            builder.systemInstruction(Content.fromParts(Part.fromText(systemMessage)));
        }

        // Tools
        if (availableTools != null && !availableTools.isEmpty()) {
            List<FunctionDeclaration> declarations = availableTools.stream()
                .map(this::toFunctionDeclaration)
                .collect(Collectors.toList());
            builder.tools(List.of(Tool.builder().functionDeclarations(declarations).build()));
        }

        return builder.build();
    }

    private List<Content> buildContents(List<Message> history) {
        return history.stream()
            .filter(m -> m.role() != Role.SYSTEM)
            .map(this::toContent)
            .collect(Collectors.toList());
    }

    private Content toContent(Message msg) {
        List<Part> parts = new ArrayList<>();
        String role = "user";

        switch (msg.role()) {
            case USER:
                role = "user";
                break;
            case ASSISTANT:
                role = "model";
                break;
            case TOOL:
                role = "user";
                break;
            default:
                throw new IllegalArgumentException("Unsupported role for Gemini: " + msg.role());
        }

        if (msg.role() == Role.TOOL) {
            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put("result", msg.content());
            parts.add(Part.fromFunctionResponse(msg.toolName(), responseMap));
        } else {
            if (msg.content() != null && !msg.content().isEmpty()) {
                parts.add(Part.fromText(msg.content()));
            }
            if (msg.toolCalls() != null) {
                for (ToolCall tc : msg.toolCalls()) {
                    parts.add(Part.fromFunctionCall(tc.toolName(), tc.arguments()));
                }
            }
        }

        return Content.builder()
                .role(role)
                .parts(parts)
                .build();
    }

    private FunctionDeclaration toFunctionDeclaration(ToolDefinition tool) {
        FunctionDeclaration.Builder builder = FunctionDeclaration.builder()
            .name(tool.name())
            .description(tool.description());

        if (tool.jsonSchema() != null && !tool.jsonSchema().isEmpty()) {
            JsonObject schemaJson = new JsonObject(tool.jsonSchema());
            builder.parameters(parseSchema(schemaJson));
        }

        return builder.build();
    }

    private Schema parseSchema(JsonObject json) {
        Schema.Builder builder = Schema.builder();
        String typeStr = json.getString("type");
        if (typeStr != null) {
            builder.type(new Type(typeStr.toUpperCase()));
        }

        builder.description(json.getString("description"));

        JsonObject properties = json.getJsonObject("properties");
        if (properties != null) {
            Map<String, Schema> propMap = new HashMap<>();
            for (String key : properties.fieldNames()) {
                propMap.put(key, parseSchema(properties.getJsonObject(key)));
            }
            builder.properties(propMap);
        }

        if (json.containsKey("required")) {
            List<String> required = json.getJsonArray("required").stream().map(Object::toString).collect(Collectors.toList());
            builder.required(required);
        }

        if (json.containsKey("items")) {
            builder.items(parseSchema(json.getJsonObject("items")));
        }

        return builder.build();
    }

    private ModelResponse toModelResponse(GenerateContentResponse response) {
        String text = response.text();
        List<ToolCall> toolCalls = new ArrayList<>();

        if (response.candidates().isPresent()) {
            List<Candidate> candidates = response.candidates().get();
            if (!candidates.isEmpty()) {
                Candidate candidate = candidates.get(0);
                if (candidate.content().isPresent()) {
                    Content candContent = candidate.content().get();
                    if (candContent.parts().isPresent()) {
                        for (Part part : candContent.parts().get()) {
                            if (part.functionCall().isPresent()) {
                                FunctionCall fc = part.functionCall().get();
                                toolCalls.add(new ToolCall(
                                    fc.id().orElse(UUID.randomUUID().toString()),
                                    fc.name().orElse("unknown"),
                                    fc.args().orElse(Collections.emptyMap())
                                ));
                            }
                        }
                    }
                }
            }
        }

        int pt = 0;
        int ct = 0;
        if (response.usageMetadata().isPresent()) {
            GenerateContentResponseUsageMetadata meta = response.usageMetadata().get();
            pt = meta.promptTokenCount().orElse(0);
            ct = meta.candidatesTokenCount().orElse(0);
        }

        return new ModelResponse(text != null ? text : "", toolCalls, new TokenUsage(pt, ct));
    }

    private Throwable wrapException(Throwable throwable) {
        return new LLMException(throwable.getMessage(), null, null, null, throwable);
    }
}
