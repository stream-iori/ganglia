package work.ganglia.port.mcp;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record McpInitializeRequest(
    String protocolVersion, Map<String, Object> capabilities, Implementation clientInfo) {
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Implementation(String name, String version) {}
}
