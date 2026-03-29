package work.ganglia.ui;

import java.util.List;

/**
 * Immutable snapshot of a completed tool execution, used by {@link DetailView} to display full
 * output.
 */
public record ToolCard(
    String toolName,
    String paramsDisplay,
    List<String> outputLines,
    String result,
    boolean isError,
    long durationMs) {}
