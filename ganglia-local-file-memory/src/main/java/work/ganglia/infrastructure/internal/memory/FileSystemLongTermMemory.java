package work.ganglia.infrastructure.internal.memory;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;

import work.ganglia.port.internal.memory.LongTermMemory;

/** File system implementation of the knowledge base. */
public class FileSystemLongTermMemory implements LongTermMemory {
  private final Vertx vertx;
  private final String baseDir;
  private final String defaultFileName;

  private static final String DEFAULT_TEMPLATE =
      """
            # Project Memory

            ## Project Conventions

            ## Architecture Decisions

            ## Lessons Learned

            """;

  private static final String USER_PROFILE_TEMPLATE =
      """
            # User Profile

            ## Communication Style

            ## Technical Background

            ## Preferences

            """;

  /**
   * Creates a FileSystemLongTermMemory with a base directory and default file name.
   *
   * @param vertx The Vertx instance.
   * @param baseDir The base directory for knowledge topics.
   * @param defaultFileName The default file name for the "project" topic.
   */
  public FileSystemLongTermMemory(Vertx vertx, String baseDir, String defaultFileName) {
    this.vertx = vertx;
    this.baseDir = baseDir != null ? baseDir : ".";
    this.defaultFileName = defaultFileName != null ? defaultFileName : ".ganglia/memory/MEMORY.md";
  }

  /**
   * Creates a FileSystemLongTermMemory with a specific file path for the default project topic.
   *
   * @param vertx The Vertx instance.
   * @param filePath The file path for the default topic.
   */
  public FileSystemLongTermMemory(Vertx vertx, String filePath) {
    this.vertx = vertx;
    if (filePath != null && filePath.contains("/")) {
      int lastSlash = filePath.lastIndexOf('/');
      this.baseDir = filePath.substring(0, lastSlash);
      this.defaultFileName = filePath.substring(lastSlash + 1);
    } else {
      this.baseDir = ".";
      this.defaultFileName = filePath != null ? filePath : ".ganglia/memory/MEMORY.md";
    }
  }

  /**
   * Creates a FileSystemLongTermMemory with default settings.
   *
   * @param vertx The Vertx instance.
   */
  public FileSystemLongTermMemory(Vertx vertx) {
    this(vertx, ".ganglia/memory/MEMORY.md");
  }

  private String getFilePath(String topic) {
    if (DEFAULT_TOPIC.equals(topic)) {
      return baseDir + "/" + defaultFileName;
    }
    if (USER_PROFILE_TOPIC.equals(topic)) {
      return baseDir + "/USER.md";
    }
    return baseDir + "/" + topic + ".md";
  }

  private String getTemplate(String topic) {
    if (USER_PROFILE_TOPIC.equals(topic)) {
      return USER_PROFILE_TEMPLATE;
    }
    return DEFAULT_TEMPLATE;
  }

  @Override
  public Future<Void> ensureInitialized(String topic) {
    String filePath = getFilePath(topic);
    return work.ganglia.util.FileSystemUtil.ensureFileWithDefault(
        vertx, filePath, getTemplate(topic));
  }

  @Override
  public Future<String> read(String topic) {
    String filePath = getFilePath(topic);
    return vertx
        .fileSystem()
        .readFile(filePath)
        .map(Buffer::toString)
        .recover(err -> Future.succeededFuture("")); // Return empty if error
  }

  @Override
  public Future<Void> append(String topic, String content) {
    String filePath = getFilePath(topic);
    return ensureInitialized(topic)
        .compose(
            v ->
                vertx
                    .fileSystem()
                    .open(filePath, new io.vertx.core.file.OpenOptions().setAppend(true)))
        .compose(
            asyncFile ->
                asyncFile
                    .write(Buffer.buffer("\n" + content + "\n"))
                    .compose(v -> asyncFile.close()));
  }

  @Override
  public Future<Void> replace(String topic, String content) {
    String filePath = getFilePath(topic);
    return ensureInitialized(topic)
        .compose(v -> vertx.fileSystem().writeFile(filePath, Buffer.buffer(content)));
  }
}
