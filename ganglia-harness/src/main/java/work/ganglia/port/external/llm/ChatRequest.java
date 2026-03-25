package work.ganglia.port.external.llm;

import java.util.Collections;
import java.util.List;

import work.ganglia.port.chat.Message;
import work.ganglia.port.external.tool.ToolDefinition;
import work.ganglia.port.internal.state.AgentSignal;

/** Encapsulates all data required for an LLM chat request. */
public record ChatRequest(
    List<Message> messages, List<ToolDefinition> tools, ModelOptions options, AgentSignal signal) {
  public ChatRequest {
    if (messages == null) {
      messages = Collections.emptyList();
    }
    if (tools == null) {
      tools = Collections.emptyList();
    }
    if (signal == null) {
      signal = new AgentSignal();
    }
  }
}
