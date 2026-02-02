package me.stream.ganglia.core.tools.model;

/**
 * Definition of a tool available to the model.
 */
public record ToolDefinition(
    String name,
    String description,
    String jsonSchema, // The JSON Schema defining the arguments
    ToolType type // BUILTIN or EXTENSION
) {}
