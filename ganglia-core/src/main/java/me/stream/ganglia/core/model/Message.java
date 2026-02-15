package me.stream.ganglia.core.model;

import me.stream.ganglia.tools.model.ToolCall;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Represents a single message in the conversation history.
 */
public record Message(
    String id,
    Role role, // SYSTEM, USER, ASSISTANT, TOOL
    String content,
    List<ToolCall> toolCalls, // Present if role is ASSISTANT and tools are called
    String toolCallId, // Present if role is TOOL (linking to the call)
    String toolName, // Present if role is TOOL
    Instant timestamp
) {
    public static Message user(String content) {
        return new Message(UUID.randomUUID().toString(), Role.USER, content, null, null, null, Instant.now());
    }

    public static Message assistant(String content) {
        return assistant(content, null);
    }

    public static Message assistant(String content, List<ToolCall> toolCalls) {
        return new Message(UUID.randomUUID().toString(), Role.ASSISTANT, content, toolCalls, null, null, Instant.now());
    }

    public static Message tool(String toolCallId, String content) {
        return tool(toolCallId, null, content);
    }

    public static Message tool(String toolCallId, String toolName, String content) {
        return new Message(UUID.randomUUID().toString(), Role.TOOL, content, null, toolCallId, toolName, Instant.now());
    }
}
