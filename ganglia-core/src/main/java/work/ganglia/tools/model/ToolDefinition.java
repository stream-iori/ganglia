package work.ganglia.tools.model;

/**
 * Definition of a tool available to the model.
 */
public record ToolDefinition(
    String name,
    String description,
    String jsonSchema, // The JSON Schema defining the arguments
    boolean isInterrupt // Whether this tool pauses execution
) {
    // Constructor for backward compatibility (defaults to false)
    public ToolDefinition(String name, String description, String jsonSchema) {
        this(name, description, jsonSchema, false);
    }
}
