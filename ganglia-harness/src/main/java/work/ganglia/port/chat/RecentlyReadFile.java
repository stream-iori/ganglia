package work.ganglia.port.chat;

/**
 * Tracks a recently read file for post-compression restoration.
 *
 * <p>After compression, recently accessed files can be re-read to restore context continuity. Files
 * are sorted by timestamp (most recent first) for restoration priority.
 *
 * @param filePath the absolute path to the file
 * @param timestamp when the file was read (epoch millis)
 * @param estimatedTokens estimated token count of the file content when read
 */
public record RecentlyReadFile(String filePath, long timestamp, int estimatedTokens)
    implements Comparable<RecentlyReadFile> {

  @Override
  public int compareTo(RecentlyReadFile other) {
    // Sort by timestamp descending (most recent first)
    return Long.compare(other.timestamp, this.timestamp);
  }

  /**
   * Creates a new RecentlyReadFile with the current timestamp.
   *
   * @param filePath the file path
   * @param estimatedTokens the estimated token count
   * @return a new RecentlyReadFile
   */
  public static RecentlyReadFile now(String filePath, int estimatedTokens) {
    return new RecentlyReadFile(filePath, System.currentTimeMillis(), estimatedTokens);
  }
}
