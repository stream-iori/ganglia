package me.stream.ganglia.core.model;

import java.time.Instant;
import java.util.List;

/**
 * Represents a single message in the conversation history.
 */
public record Message(
    String id,
    Role role, // SYSTEM, USER, ASSISTANT, TOOL
    String content,
    List<ToolCall> toolCalls, // Present if role is ASSISTANT and tools are called
    String toolCallId, // Present if role is TOOL (linking to the call)
    Instant timestamp
) {
    public static Message user(String content) {
        return new Message(java.util.UUID.randomUUID().toString(), Role.USER, content, null, null, Instant.now());
    }

    public static Message assistant(String content, List<ToolCall> toolCalls) {
        return new Message(java.util.UUID.randomUUID().toString(), Role.ASSISTANT, content, toolCalls, null, Instant.now());
    }

    public static Message tool(String toolCallId, String content) {
        return new Message(java.util.UUID.randomUUID().toString(), Role.TOOL, content, null, toolCallId, Instant.now());
    }
}
