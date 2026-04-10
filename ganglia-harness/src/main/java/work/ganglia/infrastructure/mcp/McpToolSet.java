package work.ganglia.infrastructure.mcp;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ObservationType;
import work.ganglia.port.external.tool.ToolDefinition;
import work.ganglia.port.external.tool.ToolSet;
import work.ganglia.port.external.tool.model.ToolInvokeResult;
import work.ganglia.port.internal.state.ExecutionContext;
import work.ganglia.port.internal.state.ObservationDispatcher;
import work.ganglia.port.mcp.McpCallToolRequest;
import work.ganglia.port.mcp.McpClient;

public class McpToolSet implements ToolSet {

  private final McpClient client;
  private final List<ToolDefinition> definitions;
  private volatile ObservationDispatcher dispatcher;
  private final String serverName;

  private McpToolSet(
      McpClient client,
      List<ToolDefinition> definitions,
      ObservationDispatcher dispatcher,
      String serverName) {
    this.client = client;
    this.definitions = definitions;
    this.dispatcher = dispatcher;
    this.serverName = serverName;
  }

  /** Late-binds the observation dispatcher so MCP calls are traced. */
  public void setDispatcher(ObservationDispatcher dispatcher) {
    this.dispatcher = dispatcher;
  }

  /** Creates an McpToolSet by fetching the tools from the MCP Server. */
  public static Future<McpToolSet> create(McpClient client) {
    return create(client, null, null);
  }

  /** Creates an McpToolSet with observation dispatcher and server name. */
  public static Future<McpToolSet> create(
      McpClient client, ObservationDispatcher dispatcher, String serverName) {
    return client
        .listTools()
        .map(
            result -> {
              List<ToolDefinition> defs =
                  result.tools().stream()
                      .map(
                          t ->
                              new ToolDefinition(
                                  t.name(),
                                  t.description(),
                                  new JsonObject(t.inputSchema())
                                      .encode() // convert schema Map to JSON string
                                  ))
                      .collect(Collectors.toList());
              return new McpToolSet(client, defs, dispatcher, serverName);
            });
  }

  @Override
  public List<ToolDefinition> getDefinitions() {
    return definitions;
  }

  @Override
  public Future<ToolInvokeResult> execute(
      String toolName,
      Map<String, Object> args,
      SessionContext context,
      ExecutionContext executionContext) {
    long startMs = System.currentTimeMillis();
    String sessionId = executionContext != null ? executionContext.sessionId() : null;
    String mcpSpanId = "mcp-" + java.util.UUID.randomUUID().toString().substring(0, 8);
    String parentSpanId = executionContext != null ? executionContext.spanId() : null;

    if (dispatcher != null && sessionId != null) {
      Map<String, Object> startData = new HashMap<>();
      startData.put("toolName", toolName);
      if (serverName != null) {
        startData.put("serverName", serverName);
      }
      dispatcher.dispatch(
          sessionId,
          ObservationType.MCP_CALL_STARTED,
          toolName,
          startData,
          mcpSpanId,
          parentSpanId);
    }

    McpCallToolRequest request = new McpCallToolRequest(toolName, args);
    return client
        .callTool(request)
        .map(
            result -> {
              if (Boolean.TRUE.equals(result.isError())) {
                String errorMsg =
                    result.content().stream()
                        .map(c -> c.text() != null ? c.text() : "")
                        .collect(Collectors.joining("\n"));
                return ToolInvokeResult.error(errorMsg);
              } else {
                String output =
                    result.content().stream()
                        .map(
                            c ->
                                c.text() != null
                                    ? c.text()
                                    : (c.data() != null ? "[Image Data]" : ""))
                        .collect(Collectors.joining("\n"));
                return ToolInvokeResult.success(output);
              }
            })
        .recover(
            err ->
                Future.succeededFuture(
                    ToolInvokeResult.error("MCP Tool invocation failed: " + err.getMessage())))
        .map(
            invokeResult -> {
              if (dispatcher != null && sessionId != null) {
                Map<String, Object> finishData = new HashMap<>();
                finishData.put("toolName", toolName);
                if (serverName != null) finishData.put("serverName", serverName);
                finishData.put("status", invokeResult.status().name());
                finishData.put("durationMs", System.currentTimeMillis() - startMs);
                dispatcher.dispatch(
                    sessionId,
                    ObservationType.MCP_CALL_FINISHED,
                    toolName,
                    finishData,
                    mcpSpanId,
                    parentSpanId);
              }
              return invokeResult;
            });
  }
}
