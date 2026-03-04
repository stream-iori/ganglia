package work.ganglia.core.model;

import work.ganglia.memory.TokenCounter;
import work.ganglia.tools.model.ToolCall;

import java.time.Instant;
import java.util.Collections;
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
    ToolObservation toolObservation, // Present if role is TOOL
    Instant timestamp
) {

    public Message {
        if (toolCalls == null) {
            toolCalls = Collections.emptyList();
        }
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }

    /**
     * Counts the total tokens in this message (content + tool calls).
     */
    public int countTokens(TokenCounter counter) {
        int tokens = counter.count(content);
        if (toolCalls != null && !toolCalls.isEmpty()) {
            tokens += counter.count(toolCalls.toString());
        }
        return tokens;
    }

    /**
     * Aggregates attributes related to a tool execution result.
     */
    public record ToolObservation(String toolCallId, String toolName) {}

    public static Message user(String content) {
        return new Message(UUID.randomUUID().toString(), Role.USER, content, null, null, Instant.now());
    }

    public static Message system(String content) {
        return new Message(UUID.randomUUID().toString(), Role.SYSTEM, content, null, null, Instant.now());
    }

    public static Message assistant(String content) {
        return assistant(content, null);
    }

    public static Message assistant(String content, List<ToolCall> toolCalls) {
        return new Message(UUID.randomUUID().toString(), Role.ASSISTANT, content, toolCalls, null, Instant.now());
    }

    public static Message tool(String toolCallId, String content) {
        return tool(toolCallId, null, content);
    }

    public static Message tool(String toolCallId, String toolName, String content) {
        return new Message(UUID.randomUUID().toString(), Role.TOOL, content, null, new ToolObservation(toolCallId, toolName), Instant.now());
    }
}
