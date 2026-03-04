package work.ganglia.core.model;

import work.ganglia.tools.model.ToolCall;

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
