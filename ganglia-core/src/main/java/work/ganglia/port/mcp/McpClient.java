package work.ganglia.port.mcp;

import io.vertx.core.Future;

/** Interface representing an MCP Client. */
public interface McpClient {

  /**
   * Initializes the connection with the MCP server.
   *
   * @param request the initialize request
   * @return a future containing the server's initialize result
   */
  Future<McpInitializeResult> initialize(McpInitializeRequest request);

  /**
   * Requests the list of available tools from the server.
   *
   * @return a future containing the list of tools
   */
  Future<McpListToolsResult> listTools();

  /**
   * Calls a specific tool on the server.
   *
   * @param request the tool call request
   * @return a future containing the result of the tool execution
   */
  Future<McpCallToolResult> callTool(McpCallToolRequest request);

  /**
   * Sends a ping request to the server.
   *
   * @return a future that completes when the server responds
   */
  Future<Void> ping();

  /**
   * Closes the client and underlying connection.
   *
   * @return a future that completes when closed
   */
  Future<Void> close();
}
