package me.stream.ganglia.core.model;

import me.stream.ganglia.tools.model.ToolCall;

import java.util.Collections;
import java.util.List;

public record ModelResponse(
    String content,
    List<ToolCall> toolCalls,
    TokenUsage usage
) {
    public ModelResponse {
        if (toolCalls == null) toolCalls = Collections.emptyList();
    }
}
