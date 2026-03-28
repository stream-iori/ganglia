package work.ganglia.port.chat;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.util.TokenCounter;

/** Represents a single message in the conversation history. */
public record Message(
    String id,
    Role role, // SYSTEM, USER, ASSISTANT, TOOL
    String content,
    List<ToolCall> toolCalls, // Present if role is ASSISTANT and tools are called
    ToolObservation toolObservation, // Present if role is TOOL
    Instant timestamp) {

  public Message {
    toolCalls = toolCalls == null ? Collections.emptyList() : List.copyOf(toolCalls);
    if (timestamp == null) {
      timestamp = Instant.now();
    }
  }

  /** Counts the total tokens in this message (content + tool calls). */
  public int countTokens(TokenCounter counter) {
    int tokens = counter.count(content);
    if (toolCalls != null && !toolCalls.isEmpty()) {
      tokens += counter.count(toolCalls.toString());
    }
    return tokens;
  }

  /**
   * Aggregates attributes related to a tool execution result.
   *
   * @param outputCapped true when the content has already been size-capped by
   *     ObservationCompressionHook; prevents a second truncation pass in StandardPromptEngine.
   */
  public record ToolObservation(String toolCallId, String toolName, boolean outputCapped) {
    /** Convenience constructor for the common case where no capping has occurred. */
    public ToolObservation(String toolCallId, String toolName) {
      this(toolCallId, toolName, false);
    }
  }

  public static Message user(String content) {
    return new Message(UUID.randomUUID().toString(), Role.USER, content, null, null, Instant.now());
  }

  public static Message system(String content) {
    return new Message(
        UUID.randomUUID().toString(), Role.SYSTEM, content, null, null, Instant.now());
  }

  public static Message assistant(String content) {
    return assistant(content, null);
  }

  public static Message assistant(String content, List<ToolCall> toolCalls) {
    return new Message(
        UUID.randomUUID().toString(), Role.ASSISTANT, content, toolCalls, null, Instant.now());
  }

  public static Message tool(String toolCallId, String content) {
    return tool(toolCallId, null, content);
  }

  public static Message tool(String toolCallId, String toolName, String content) {
    return new Message(
        UUID.randomUUID().toString(),
        Role.TOOL,
        content,
        null,
        new ToolObservation(toolCallId, toolName),
        Instant.now());
  }

  /**
   * Creates a TOOL message whose output has already been size-capped by the interceptor pipeline.
   * {@code StandardPromptEngine} will skip the redundant truncation pass for such messages.
   */
  public static Message toolCapped(String toolCallId, String toolName, String content) {
    return new Message(
        UUID.randomUUID().toString(),
        Role.TOOL,
        content,
        null,
        new ToolObservation(toolCallId, toolName, true),
        Instant.now());
  }
}
