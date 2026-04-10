package work.ganglia.port.external.llm;

import java.util.Collections;
import java.util.List;

import work.ganglia.port.chat.Message;
import work.ganglia.port.external.tool.ToolDefinition;
import work.ganglia.port.internal.state.AgentSignal;

/** Encapsulates all data required for an LLM chat request. */
public record ChatRequest(
    String sessionId,
    List<Message> messages,
    List<ToolDefinition> tools,
    ModelOptions options,
    AgentSignal signal,
    String spanId) {
  public ChatRequest {
    messages = messages == null ? Collections.emptyList() : List.copyOf(messages);
    tools = tools == null ? Collections.emptyList() : List.copyOf(tools);
    if (signal == null) {
      signal = new AgentSignal();
    }
  }

  public ChatRequest(
      List<Message> messages,
      List<ToolDefinition> tools,
      ModelOptions options,
      AgentSignal signal) {
    this(null, messages, tools, options, signal, null);
  }
}
