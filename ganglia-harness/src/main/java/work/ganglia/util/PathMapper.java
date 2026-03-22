package work.ganglia.util;

/**
 * Interface for mapping and validating paths provided by the agent. This abstraction allows
 * different toolsets to interpret paths according to their execution environment (e.g., local
 * filesystem, Docker container, or mapped host-container workspace).
 */
public interface PathMapper {

  /**
   * Translates an input path into a target environment path and ensures it is safe.
   *
   * @param inputPath The path string provided by the LLM/Agent.
   * @return The sanitized and potentially mapped absolute path.
   * @throws SecurityException if the path is outside the allowed sandbox.
   * @throws IllegalArgumentException if the path is invalid.
   */
  String map(String inputPath);
}
