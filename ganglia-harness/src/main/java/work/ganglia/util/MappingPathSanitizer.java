package work.ganglia.util;

/**
 * A PathSanitizer that maps a virtual root directory (e.g., /workspace) to an actual root directory
 * on the host filesystem before validating and returning the path. This is useful when the agent
 * works with virtual paths (e.g., inside a Docker sandbox) but the tools operate on the
 * bind-mounted host filesystem.
 */
public class MappingPathSanitizer extends PathSanitizer {

  private final String virtualRoot;
  private final String actualRoot;

  public MappingPathSanitizer(String virtualRoot, String actualRoot) {
    super(actualRoot);
    this.virtualRoot = virtualRoot.endsWith("/") ? virtualRoot : virtualRoot + "/";
    this.actualRoot = actualRoot.endsWith("/") ? actualRoot : actualRoot + "/";
  }

  @Override
  public String sanitize(String inputPath) {
    if (inputPath == null || inputPath.isEmpty()) {
      throw new IllegalArgumentException("Path cannot be empty");
    }

    String mappedPath = inputPath;

    // Normalize string representation to handle basic cases
    String normalizedInput = inputPath.replace("\\", "/");

    // If the input path starts with the virtual root, replace it with the actual root
    if (normalizedInput.equals(virtualRoot.substring(0, virtualRoot.length() - 1))
        || normalizedInput.equals(virtualRoot)) {
      mappedPath = actualRoot;
    } else if (normalizedInput.startsWith(virtualRoot)) {
      mappedPath = actualRoot + normalizedInput.substring(virtualRoot.length());
    }

    // Delegate to the standard PathSanitizer to handle absolute path resolution
    // and ensure it does not escape the actualRoot.
    return super.sanitize(mappedPath);
  }
}
