package work.ganglia.util;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Utility for ensuring file operations stay within the project sandbox. */
public class PathSanitizer {

  private final String projectRoot;

  public PathSanitizer() {
    this(System.getProperty("user.dir"));
  }

  public PathSanitizer(String projectRoot) {
    this.projectRoot = projectRoot;
  }

  /**
   * Validates that a path is within the project root and returns its absolute form.
   *
   * @param inputPath The path provided by the agent.
   * @return Absolute path string if valid.
   * @throws SecurityException If the path escapes the project root.
   */
  public String sanitize(String inputPath) {
    if (inputPath == null || inputPath.isEmpty()) {
      throw new IllegalArgumentException("Path cannot be empty");
    }

    try {
      // 1. Resolve root path
      Path rootPath = Paths.get(projectRoot).toAbsolutePath().normalize();
      boolean isVirtual = false;
      try {
        rootPath = rootPath.toRealPath();
      } catch (IOException e) {
        // If it doesn't exist, it might be a virtual root (e.g., /workspace in Docker)
        isVirtual = true;
      }

      // 2. Resolve requested path
      Path requested = Paths.get(inputPath);
      if (!requested.isAbsolute()) {
        requested = rootPath.resolve(inputPath);
      }
      requested = requested.normalize();

      String absoluteRequestedStr;
      if (isVirtual) {
        // For virtual roots, we can only do string/normalization based checks
        absoluteRequestedStr = requested.toString();
      } else {
        try {
          absoluteRequestedStr = requested.toRealPath().toString();
        } catch (IOException e) {
          // If it doesn't exist, try resolving via parents or fallback to normalization
          Path resolved = null;
          Path tempPath = requested;
          Path parent = tempPath.getParent();
          while (parent != null) {
            try {
              Path realParent = parent.toRealPath();
              resolved = realParent.resolve(parent.relativize(tempPath)).normalize();
              break;
            } catch (IOException ignored) {
              parent = parent.getParent();
            }
          }
          absoluteRequestedStr =
              (resolved != null) ? resolved.toString() : requested.toAbsolutePath().toString();
        }
      }

      // 3. Project root check
      String rootStr = rootPath.toString();
      if (!absoluteRequestedStr.startsWith(rootStr)) {
        throw new SecurityException(
            "Access denied: Path escapes project root (" + rootStr + "): " + inputPath);
      }

      return absoluteRequestedStr;
    } catch (SecurityException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Failed to resolve path: " + inputPath, e);
    }
  }

  /**
   * Sanitizes a string for safe use in a shell command. Prevents shell injection by escaping
   * special characters.
   */
  public static String escapeShellArg(String arg) {
    if (arg == null) return "''";
    if (arg.isEmpty()) return "''";

    // Wrap in single quotes and escape existing single quotes
    // ' becomes '\''
    return "'" + arg.replace("'", "'\\''") + "'";
  }
}
