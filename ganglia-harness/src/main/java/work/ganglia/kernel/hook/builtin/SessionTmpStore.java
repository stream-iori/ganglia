package work.ganglia.kernel.hook.builtin;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;

import work.ganglia.util.Constants;

/**
 * Writes raw tool output to a session-scoped temporary file under {@code
 * {projectRoot}/.ganglia/tmp/{sessionId}/{toolCallId}.txt} and returns a concise path + line-count
 * hint for the agent.
 *
 * <p>Files are session-scoped: every session gets its own subdirectory. The tmp tree is not cleaned
 * up by this class; session teardown (via {@code SessionManager.deleteSession}) is responsible for
 * GC.
 */
public class SessionTmpStore {
  private static final Logger log = LoggerFactory.getLogger(SessionTmpStore.class);

  private final Vertx vertx;
  private final String projectRoot;

  public SessionTmpStore(Vertx vertx, String projectRoot) {
    this.vertx = vertx;
    this.projectRoot = projectRoot;
  }

  /**
   * Writes {@code rawOutput} to a tmp file and returns a hint message that contains the file path
   * and line count. The returned future always succeeds; on write failure it returns a plain
   * truncation hint instead.
   */
  public Future<String> store(
      String sessionId, String toolCallId, String toolName, String rawOutput) {
    Path sessionTmpDir =
        Paths.get(projectRoot, Constants.DIR_TMP, sessionId).toAbsolutePath().normalize();
    String fileName = sanitize(toolCallId) + ".txt";
    String filePath = sessionTmpDir.resolve(fileName).toString();

    int lineCount = countLines(rawOutput);

    return vertx
        .fileSystem()
        .mkdirs(sessionTmpDir.toString())
        .compose(v -> vertx.fileSystem().writeFile(filePath, Buffer.buffer(rawOutput)))
        .map(
            v -> {
              log.debug(
                  "Wrote {} lines of '{}' output to tmp file: {}", lineCount, toolName, filePath);
              return buildHint(toolName, filePath, lineCount);
            })
        .recover(
            err -> {
              log.warn(
                  "Failed to write tmp file for tool '{}', returning path hint without file: {}",
                  toolName,
                  err.getMessage());
              // Return hint without a valid path — agent will fall back to re-invoking the tool
              return Future.succeededFuture(
                  "[Full output could not be saved. Re-invoke '"
                      + toolName
                      + "' to retrieve content.]");
            });
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  public static String buildHint(String toolName, String filePath, int lineCount) {
    return "[Output from '"
        + toolName
        + "' was large ("
        + lineCount
        + " lines) and has been saved to: "
        + filePath
        + "]\n"
        + "[Use read_file(\""
        + filePath
        + "\", offset=0, limit=200) to read sections of this file.]";
  }

  public static int countLines(String text) {
    if (text == null || text.isEmpty()) return 0;
    int count = 1;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '\n') count++;
    }
    return count;
  }

  /** Removes characters unsafe for file names, keeping the call ID recognisable. */
  private static String sanitize(String toolCallId) {
    return toolCallId.replaceAll("[^a-zA-Z0-9_\\-]", "_");
  }
}
