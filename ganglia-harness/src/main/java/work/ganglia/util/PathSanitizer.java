package work.ganglia.util;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Utility for ensuring file operations stay within the project sandbox. */
public class PathSanitizer implements PathMapper {

  private final String projectRoot;

  public PathSanitizer() {
    this(System.getProperty("user.dir"));
  }

  public PathSanitizer(String projectRoot) {
    this.projectRoot = projectRoot;
  }

  @Override
  public String map(String inputPath) {
    return sanitize(inputPath);
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
      ResolvedRoot root = resolveRoot();
      String absolutePath = resolveRequested(inputPath, root.isVirtual, root.path);
      checkWithinRoot(absolutePath, root.path.toString(), inputPath);
      return absolutePath;
    } catch (SecurityException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Failed to resolve path: " + inputPath, e);
    }
  }

  private record ResolvedRoot(Path path, boolean isVirtual) {}

  private ResolvedRoot resolveRoot() {
    Path rootPath = Paths.get(projectRoot).toAbsolutePath().normalize();
    try {
      return new ResolvedRoot(rootPath.toRealPath(), false);
    } catch (IOException e) {
      // If it doesn't exist, it might be a virtual root (e.g., /workspace in Docker)
      return new ResolvedRoot(rootPath, true);
    }
  }

  private String resolveRequested(String inputPath, boolean isVirtual, Path rootPath) {
    Path requested = Paths.get(inputPath);
    if (!requested.isAbsolute()) {
      requested = rootPath.resolve(inputPath);
    }
    requested = requested.normalize();

    if (isVirtual) {
      return requested.toString();
    }

    try {
      return requested.toRealPath().toString();
    } catch (IOException e) {
      // Path doesn't exist yet — try resolving via closest existing parent
      return resolveViaParent(requested);
    }
  }

  private String resolveViaParent(Path requested) {
    Path parent = requested.getParent();
    while (parent != null) {
      try {
        Path realParent = parent.toRealPath();
        return realParent.resolve(parent.relativize(requested)).normalize().toString();
      } catch (IOException ignored) {
        parent = parent.getParent();
      }
    }
    return requested.toAbsolutePath().toString();
  }

  private void checkWithinRoot(String absolutePath, String rootStr, String inputPath) {
    if (!absolutePath.startsWith(rootStr)) {
      throw new SecurityException(
          "Access denied: Path escapes project root (" + rootStr + "): " + inputPath);
    }
  }

  /**
   * Sanitizes a string for safe use in a shell command. Prevents shell injection by escaping
   * special characters.
   */
  public static String escapeShellArg(String arg) {
    if (arg == null) {
      return "''";
    }
    if (arg.isEmpty()) {
      return "''";
    }

    // Wrap in single quotes and escape existing single quotes
    // ' becomes '\''
    return "'" + arg.replace("'", "'\\''") + "'";
  }
}
