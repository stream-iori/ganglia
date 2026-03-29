package work.ganglia.infrastructure.external.llm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.codec.BodyCodec;

import work.ganglia.infrastructure.external.llm.util.JsonSanitizer;
import work.ganglia.port.chat.Message;
import work.ganglia.port.external.llm.ChatRequest;
import work.ganglia.port.external.llm.ModelOptions;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.external.tool.ToolDefinition;
import work.ganglia.port.internal.state.TokenUsage;

public class AnthropicModelGateway extends AbstractModelGateway {
  private static final Logger logger = LoggerFactory.getLogger(AnthropicModelGateway.class);
  private static final Logger llmLogger = LoggerFactory.getLogger("LLM_LOG");
  private final WebClient webClient;
  private final GatewayConfig config;
  private final String endpoint;

  public AnthropicModelGateway(Vertx vertx, WebClient webClient, String apiKey, String baseUrl) {
    this(vertx, webClient, new GatewayConfig(apiKey, baseUrl, 60000));
  }

  public AnthropicModelGateway(Vertx vertx, WebClient webClient, GatewayConfig config) {
    super(vertx);
    this.webClient = webClient;
    this.config = config;
    this.endpoint = normalizeEndpoint(config.baseUrl(), "v1/messages");
  }

  public AnthropicModelGateway(
      Vertx vertx, WebClient webClient, String apiKey, String baseUrl, int timeoutMs) {
    this(vertx, webClient, new GatewayConfig(apiKey, baseUrl, timeoutMs));
  }

  @Override
  public Future<ModelResponse> chat(ChatRequest request) {
    JsonObject payload =
        buildPayload(request.messages(), request.tools(), request.options(), false);
    llmLogger.info(
        "[REQ] [ANTHROPIC] Model: {}, Payload: {}",
        request.options().modelName(),
        payload.encode());
    return withSemaphore(
        () ->
            webClient
                .postAbs(endpoint)
                .putHeader("x-api-key", config.apiKey())
                .putHeader("anthropic-version", "2023-06-01")
                .putHeader("Content-Type", "application/json")
                .timeout(config.timeout())
                .as(BodyCodec.jsonObject())
                .sendJsonObject(payload)
                .compose(
                    response -> {
                      if (request.signal().isAborted()) {
                        return Future.failedFuture(
                            new work.ganglia.kernel.loop.AgentAbortedException());
                      }
                      if (response.statusCode() >= 400) {
                        return Future.failedFuture(
                            new LLMException(
                                "Anthropic Error: " + response.statusMessage(),
                                null,
                                response.statusCode(),
                                response.bodyAsJsonObject() != null
                                    ? response.bodyAsJsonObject().encode()
                                    : null,
                                null));
                      }
                      try {
                        return Future.succeededFuture(toModelResponse(response.body()));
                      } catch (Exception e) {
                        return Future.failedFuture(wrapException(e));
                      }
                    }));
  }

  @Override
  public Future<ModelResponse> chatStream(
      ChatRequest request, work.ganglia.port.internal.state.ExecutionContext context) {
    JsonObject payload = buildPayload(request.messages(), request.tools(), request.options(), true);
    llmLogger.info(
        "[STREAM_REQ] [ANTHROPIC] Session: {}, Model: {}, Payload: {}",
        context.sessionId(),
        request.options().modelName(),
        payload.encode());

    StringBuilder fullContent = new StringBuilder();
    Map<Integer, ToolCallBuilder> toolCallBuilders = new HashMap<>();
    int[] usage = new int[2]; // [input, output]

    return executeStreamingRequest(
        request,
        context,
        payload,
        webClient
            .postAbs(endpoint)
            .putHeader("x-api-key", config.apiKey())
            .putHeader("anthropic-version", "2023-06-01")
            .putHeader("Content-Type", "application/json")
            .timeout(config.timeout()),
        json -> {
          try {
            String type = json.getString("type");
            if (type == null) {
              return;
            }

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
                  ToolCallBuilder builder =
                      toolCallBuilders.computeIfAbsent(index, k -> new ToolCallBuilder());
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
                JsonObject msgUsage = json.getJsonObject("usage");
                if (msgUsage != null) {
                  usage[1] = msgUsage.getInteger("output_tokens", 0);
                }
                break;
            }
          } catch (Exception e) {
            logger.error("Failed to parse Anthropic SSE chunk", e);
          }
        },
        () -> buildStreamingResponse(fullContent, toolCallBuilders, usage[0], usage[1]));
  }

  private JsonObject buildPayload(
      List<Message> history,
      List<ToolDefinition> availableTools,
      ModelOptions options,
      boolean stream) {
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
        obj.put(
            "content",
            new JsonArray().add(new JsonObject().put("type", "text").put("text", msg.content())));
        break;
      case ASSISTANT:
        obj.put("role", "assistant");
        JsonArray contentArr = new JsonArray();
        if (msg.content() != null && !msg.content().isEmpty()) {
          contentArr.add(new JsonObject().put("type", "text").put("text", msg.content()));
        }
        if (msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
          for (ToolCall tc : msg.toolCalls()) {
            contentArr.add(
                new JsonObject()
                    .put("type", "tool_use")
                    .put("id", tc.id())
                    .put("name", tc.toolName())
                    .put("input", new JsonObject(tc.arguments())));
          }
        }
        obj.put("content", contentArr);
        break;
      case TOOL:
        obj.put("role", "user");
        JsonArray toolResArr = new JsonArray();
        toolResArr.add(
            new JsonObject()
                .put("type", "tool_result")
                .put("tool_use_id", msg.toolObservation().toolCallId())
                .put("content", msg.content() != null ? msg.content() : ""));
        obj.put("content", toolResArr);
        break;
      default:
        throw new IllegalArgumentException(
            "Unsupported role for Anthropic messages: " + msg.role());
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
      obj.put("input_schema", new JsonObject(JsonSanitizer.sanitize(tool.jsonSchema())));
    } else {
      obj.put(
          "input_schema",
          new JsonObject().put("type", "object").put("properties", new JsonObject()));
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
        toolCalls.add(
            new ToolCall(id, name, input != null ? input.getMap() : Collections.emptyMap()));
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
}
