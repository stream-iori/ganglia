package me.stream.ganglia.skills;

/**
 * Represents a tool defined within a skill that executes an external script.
 */
public record ScriptToolDefinition(
    String name,
    String description,
    String command,
    String schema
) {}
