package me.stream.ganglia.tools.subagent;

import java.util.List;

/**
 * Represents a Directed Acyclic Graph (DAG) of sub-tasks.
 */
public record TaskGraph(
    List<TaskNode> nodes
) {}
