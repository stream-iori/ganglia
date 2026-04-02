package work.ganglia.infrastructure.internal.state;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.file.FileSystem;

import work.ganglia.port.chat.Message;
import work.ganglia.port.chat.RecentlyReadFile;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.internal.state.FileRestorationService;
import work.ganglia.util.TokenCounter;

/**
 * Default implementation of FileRestorationService using Vert.x FileSystem.
 *
 * <p>This service tracks recently read files and can re-read them after compression to restore
 * context. Files are sorted by most recent first and restored within token budget constraints.
 */
public class DefaultFileRestorationService implements FileRestorationService {
  private static final Logger logger = LoggerFactory.getLogger(DefaultFileRestorationService.class);

  private static final String KEY_RECENTLY_READ_FILES = "recentlyReadFiles";
  private static final int MAX_TRACKED_FILES = 10;

  private final Vertx vertx;
  private final FileSystem fileSystem;
  private final TokenCounter tokenCounter;

  public DefaultFileRestorationService(Vertx vertx, TokenCounter tokenCounter) {
    this.vertx = vertx;
    this.fileSystem = vertx.fileSystem();
    this.tokenCounter = tokenCounter;
  }

  @Override
  public Future<List<Message>> restoreRecentFiles(
      SessionContext context, int maxFiles, int totalTokenBudget, int perFileTokenLimit) {

    List<RecentlyReadFile> recentFiles = getRecentlyReadFiles(context);

    // Filter out excluded files
    List<RecentlyReadFile> eligible =
        recentFiles.stream()
            .filter(f -> !shouldExcludeFromRestore(f.filePath()))
            .collect(Collectors.toList());

    if (eligible.isEmpty()) {
      return Future.succeededFuture(Collections.emptyList());
    }

    // Limit to maxFiles
    List<RecentlyReadFile> toRestore = eligible.stream().limit(maxFiles).toList();

    logger.info(
        "Attempting to restore {} recent files (budget: {} tokens, per-file: {})",
        toRestore.size(),
        totalTokenBudget,
        perFileTokenLimit);

    return restoreFilesWithBudget(toRestore, totalTokenBudget, perFileTokenLimit);
  }

  private Future<List<Message>> restoreFilesWithBudget(
      List<RecentlyReadFile> files, int totalBudget, int perFileLimit) {

    List<Future<RestoredFile>> futures = new ArrayList<>();
    int remainingBudget = totalBudget;

    for (RecentlyReadFile file : files) {
      if (remainingBudget <= 0) {
        break;
      }
      int allocatedTokens = Math.min(perFileLimit, remainingBudget);
      futures.add(readFileWithLimit(file.filePath(), allocatedTokens));
      remainingBudget -= allocatedTokens;
    }

    return Future.join(futures)
        .map(
            v -> {
              List<Message> messages = new ArrayList<>();
              for (Future<RestoredFile> f : futures) {
                try {
                  RestoredFile rf = f.result();
                  if (rf != null && rf.content() != null && !rf.content().isEmpty()) {
                    String content =
                        String.format(
                            "--- %s ---\n%s",
                            rf.filePath(), truncateToTokens(rf.content(), perFileLimit));
                    messages.add(Message.system(content));
                  }
                } catch (Exception e) {
                  logger.debug("Failed to restore file: {}", e.getMessage());
                }
              }

              if (!messages.isEmpty()) {
                logger.info("Restored {} files after compression", messages.size());
              }

              return messages;
            });
  }

  private Future<RestoredFile> readFileWithLimit(String filePath, int tokenLimit) {
    return fileSystem
        .readFile(filePath)
        .map(
            buffer -> {
              if (buffer == null) {
                return null;
              }
              String content = buffer.toString();
              return new RestoredFile(filePath, content);
            })
        .recover(
            err -> {
              logger.debug(
                  "Could not read file {} for restoration: {}", filePath, err.getMessage());
              return Future.succeededFuture(null);
            });
  }

  private String truncateToTokens(String content, int maxTokens) {
    int currentTokens = tokenCounter.count(content);
    if (currentTokens <= maxTokens) {
      return content;
    }

    // Binary search for truncation point
    int lo = 0;
    int hi = content.length();
    while (lo < hi - 1) {
      int mid = (lo + hi) / 2;
      if (tokenCounter.count(content.substring(0, mid)) <= maxTokens) {
        lo = mid;
      } else {
        hi = mid;
      }
    }

    return content.substring(0, lo) + "\n\n[TRUNCATED]";
  }

  @Override
  public SessionContext recordFileRead(
      SessionContext context, String filePath, int estimatedTokens) {
    List<RecentlyReadFile> current = new ArrayList<>(getRecentlyReadFiles(context));

    // Remove existing entry for same path
    current.removeIf(f -> f.filePath().equals(filePath));

    // Add new entry
    current.add(RecentlyReadFile.now(filePath, estimatedTokens));

    // Sort and limit
    List<RecentlyReadFile> trimmed = current.stream().sorted().limit(MAX_TRACKED_FILES).toList();

    // Store in metadata
    List<Map<String, Object>> serialized =
        trimmed.stream()
            .map(
                r -> {
                  Map<String, Object> m = new HashMap<>();
                  m.put("filePath", r.filePath());
                  m.put("timestamp", r.timestamp());
                  m.put("estimatedTokens", r.estimatedTokens());
                  return m;
                })
            .collect(Collectors.toList());

    return context.withNewMetadata(KEY_RECENTLY_READ_FILES, serialized);
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<RecentlyReadFile> getRecentlyReadFiles(SessionContext context) {
    Object value = context.metadata().get(KEY_RECENTLY_READ_FILES);
    if (!(value instanceof List<?> list)) {
      return Collections.emptyList();
    }

    return list.stream()
        .filter(item -> item instanceof Map<?, ?>)
        .map(
            item -> {
              Map<?, ?> m = (Map<?, ?>) item;
              return new RecentlyReadFile(
                  (String) m.get("filePath"),
                  ((Number) m.get("timestamp")).longValue(),
                  ((Number) m.get("estimatedTokens")).intValue());
            })
        .sorted()
        .collect(Collectors.toList());
  }

  private record RestoredFile(String filePath, String content) {}
}
