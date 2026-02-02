package me.stream.ganglia.core.model;

import me.stream.ganglia.core.tools.model.ToolCall;

import java.util.List;

public record ModelResponse(
    String content,
    List<ToolCall> toolCalls,
    TokenUsage usage
) {}
