package work.ganglia.infrastructure.mcp;

import java.util.List;

import work.ganglia.port.external.tool.ToolSet;
import work.ganglia.port.mcp.McpClient;

/**
 * A registry of active MCP toolsets and their associated clients. This is used to manage the
 * lifecycle of MCP servers.
 */
public record McpRegistry(List<ToolSet> toolSets, List<McpClient> clients) {
  public void close() {
    for (McpClient client : clients) {
      try {
        client.close();
      } catch (Exception e) {
        // Silently close
      }
    }
  }
}
