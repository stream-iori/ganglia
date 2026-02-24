package me.stream.ganglia.tools.subagent;

import java.util.List;
import java.util.Map;

/**
 * Represents a single task to be executed by a Sub-Agent.
 *
 * @param id           Unique identifier for the node.
 * @param task         Description of the sub-task.
 * @param persona      Persona to use (e.g., INVESTIGATOR, REFACTORER, GENERAL).
 * @param dependencies List of task IDs that must complete before this one starts.
 * @param inputMapping Optional mapping to specify which dependency outputs should be used as input for this task.
 */
public record TaskNode(
    String id,
    String task,
    String persona,
    List<String> dependencies,
    Map<String, String> inputMapping
) {}
