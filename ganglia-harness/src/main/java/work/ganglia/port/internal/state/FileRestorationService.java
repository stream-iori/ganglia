package work.ganglia.port.internal.state;

import java.util.List;

import io.vertx.core.Future;

import work.ganglia.port.chat.Message;
import work.ganglia.port.chat.RecentlyReadFile;
import work.ganglia.port.chat.SessionContext;

/**
 * Service for restoring recently accessed files after context compression.
 *
 * <p>Post-compression restoration re-reads recently accessed files to maintain context continuity.
 * This helps the agent remember what files it was working with before compression occurred.
 */
public interface FileRestorationService {

  /** Default maximum number of files to restore. */
  int DEFAULT_MAX_FILES = 5;

  /** Default total token budget for restoration. */
  int DEFAULT_TOTAL_TOKEN_BUDGET = 50_000;

  /** Default maximum tokens per file. */
  int DEFAULT_PER_FILE_TOKEN_LIMIT = 5_000;

  /**
   * Restores recently accessed files as context messages.
   *
   * @param context the session context after compression
   * @param maxFiles maximum number of files to restore
   * @param totalTokenBudget total token budget for all restored files
   * @param perFileTokenLimit maximum tokens per individual file
   * @return a future containing the list of restoration messages
   */
  Future<List<Message>> restoreRecentFiles(
      SessionContext context, int maxFiles, int totalTokenBudget, int perFileTokenLimit);

  /**
   * Restores recently accessed files using default limits.
   *
   * @param context the session context after compression
   * @return a future containing the list of restoration messages
   */
  default Future<List<Message>> restoreRecentFiles(SessionContext context) {
    return restoreRecentFiles(
        context, DEFAULT_MAX_FILES, DEFAULT_TOTAL_TOKEN_BUDGET, DEFAULT_PER_FILE_TOKEN_LIMIT);
  }

  /**
   * Records that a file was read, for potential post-compression restoration.
   *
   * @param context the current session context
   * @param filePath the path to the file that was read
   * @param estimatedTokens the estimated token count of the file content
   * @return an updated session context with the file recorded
   */
  SessionContext recordFileRead(SessionContext context, String filePath, int estimatedTokens);

  /**
   * Gets the list of recently read files from the session context.
   *
   * @param context the session context
   * @return the list of recently read files, sorted by most recent first
   */
  List<RecentlyReadFile> getRecentlyReadFiles(SessionContext context);

  /** Checks if a file path should be excluded from restoration. */
  default boolean shouldExcludeFromRestore(String filePath) {
    if (filePath == null) return true;
    String lower = filePath.toLowerCase();
    // Exclude plan files, CLAUDE.md files, and hidden files
    return lower.contains("plan")
        || lower.endsWith("claude.md")
        || lower.contains("/.claude/")
        || lower.startsWith(".");
  }
}
