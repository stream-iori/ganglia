package me.stream.ganglia.core.tools.model;

/**
 * Definition of a tool available to the model.
 */
public record ToolDefinition(
    String name,
    String description,
    String jsonSchema, // The JSON Schema defining the arguments
    ToolType type, // BUILTIN or EXTENSION
    boolean isInterrupt // Whether this tool pauses execution
) {
    // Constructor for backward compatibility (defaults to false)
    public ToolDefinition(String name, String description, String jsonSchema, ToolType type) {
        this(name, description, jsonSchema, type, false);
    }
}