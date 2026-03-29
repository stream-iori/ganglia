package work.ganglia.coding.tool.util;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;

import work.ganglia.util.VertxProcess;

/** Shared utility for generating unified diffs with proper temp file cleanup. */
public class DiffGenerator {
  private static final Logger logger = LoggerFactory.getLogger(DiffGenerator.class);

  private final Vertx vertx;
  private final FileSystem fileSystem;

  public DiffGenerator(Vertx vertx) {
    this.vertx = vertx;
    this.fileSystem = vertx.fileSystem();
  }

  /**
   * Generates a unified diff between an existing file and new content.
   *
   * @param oldFilePath path to the existing file (or "/dev/null" for new files)
   * @param newContent the new file content
   * @param label display label for the diff header
   * @return a Future containing the diff string
   */
  public Future<String> generateDiff(String oldFilePath, String newContent, String label) {
    // Derive temp path from oldFilePath, but fall back to system temp when oldFilePath is a
    // special path like /dev/null that doesn't support sibling file creation.
    String tempNewPath =
        oldFilePath.startsWith("/dev/")
            ? System.getProperty("java.io.tmpdir") + "/ganglia-diff-" + System.nanoTime()
            : oldFilePath + ".new." + System.nanoTime();
    return fileSystem
        .writeFile(tempNewPath, Buffer.buffer(newContent))
        .compose(v -> executeDiffCommand(oldFilePath, tempNewPath))
        .compose(result -> formatDiffResult(result, label))
        .eventually(() -> deleteSilently(tempNewPath));
  }

  private Future<VertxProcess.Result> executeDiffCommand(String oldFilePath, String tempNewPath) {
    var command = String.format("diff -u %s %s", oldFilePath, tempNewPath);
    return VertxProcess.execute(vertx, List.of("bash", "-c", command), 10000, 1024 * 1024);
  }

  private Future<String> formatDiffResult(VertxProcess.Result result, String label) {
    String output = result.output();
    if (output.startsWith("---")) {
      String[] lines = output.split("\\n", 3);
      if (lines.length >= 2) {
        return Future.succeededFuture(
            "--- " + label + "\n" + "+++ " + label + "\n" + (lines.length > 2 ? lines[2] : ""));
      }
    }
    return Future.succeededFuture(output);
  }

  private Future<Void> deleteSilently(String path) {
    return fileSystem.delete(path).recover(err -> Future.succeededFuture());
  }
}
