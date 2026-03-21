package work.ganglia.port.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record McpInitializeResult(
    String protocolVersion, Map<String, Object> capabilities, Implementation serverInfo) {
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Implementation(String name, String version) {}
}
