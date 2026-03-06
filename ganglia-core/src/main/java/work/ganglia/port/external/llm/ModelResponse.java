package work.ganglia.port.external.llm;

import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.internal.state.TokenUsage;

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
