package me.stream.ganglia.core.model;

import java.util.List;

public record ModelResponse(
    String content,
    List<ToolCall> toolCalls,
    TokenUsage usage
) {}
