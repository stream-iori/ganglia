package work.ganglia.util;

import java.util.Objects;

/**
 * A PathMapper wrapper that enforces read-only access. Used by RealityAnchorTask to protect
 * validation scripts from being modified by agents.
 *
 * <p>Path mapping is delegated to the inner mapper; write access is unconditionally rejected.
 */
public class ReadOnlyPathMapper implements PathMapper {

  private final PathMapper delegate;

  public ReadOnlyPathMapper(PathMapper delegate) {
    this.delegate = Objects.requireNonNull(delegate, "delegate PathMapper must not be null");
  }

  @Override
  public String map(String inputPath) {
    return delegate.map(inputPath);
  }

  /**
   * @return always true — this mapper rejects all write operations
   */
  public boolean isReadOnly() {
    return true;
  }

  /**
   * Checks write access for a path. Always throws SecurityException.
   *
   * @param path the path to check
   * @throws SecurityException always — write access is not permitted
   */
  public void checkWriteAccess(String path) {
    throw new SecurityException(
        "Write access denied: RealityAnchor validation scripts are read-only. Path: " + path);
  }
}
