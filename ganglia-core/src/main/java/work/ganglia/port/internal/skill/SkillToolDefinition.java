package work.ganglia.port.internal.skill;

/**
 * Represents a tool defined within a skill.
 * Can be a script-based tool or a Java-based tool.
 */
public record SkillToolDefinition(
    String name,
    String description,
    String type,      // SCRIPT or JAVA
    ScriptInfo script,
    JavaInfo java,
    String schema
) {
    public record ScriptInfo(String command) {}
    public record JavaInfo(String className) {}
}
