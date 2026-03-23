package work.ganglia.infrastructure.internal.memory;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.ganglia.port.internal.memory.MemoryStore;
import work.ganglia.port.internal.memory.model.MemoryEntry;
import work.ganglia.port.internal.memory.model.MemoryIndexItem;
import work.ganglia.port.internal.memory.model.MemoryQuery;

public class FileSystemMemoryStore implements MemoryStore {
  private static final Logger log = LoggerFactory.getLogger(FileSystemMemoryStore.class);
  private final Vertx vertx;
  private final Path memoryDir;
  private final List<MemoryIndexItem> index;

  public FileSystemMemoryStore(Vertx vertx, String basePath) {
    this.vertx = vertx;
    this.memoryDir =
        Paths.get(basePath).resolve(".ganglia/memory/entries").toAbsolutePath().normalize();
    this.index = new CopyOnWriteArrayList<>();

    // Ensure directory exists
    if (!vertx.fileSystem().existsBlocking(this.memoryDir.toString())) {
      vertx.fileSystem().mkdirsBlocking(this.memoryDir.toString());
    }

    loadIndex();
  }

  private void loadIndex() {
    // Load existing index items from the directory synchronously during initialization
    List<String> files = vertx.fileSystem().readDirBlocking(memoryDir.toString(), ".*\\.json");
    for (String file : files) {
      try {
        String content = vertx.fileSystem().readFileBlocking(file).toString();
        MemoryEntry entry = Json.decodeValue(content, MemoryEntry.class);
        index.add(
            new MemoryIndexItem(entry.id(), entry.title(), entry.category(), entry.timestamp()));
      } catch (Exception e) {
        log.warn("Failed to load memory entry from {}", file, e);
      }
    }
    sortIndex();
    log.info("Loaded {} memory entries into index", index.size());
  }

  private void sortIndex() {
    index.sort(Comparator.comparing(MemoryIndexItem::timestamp).reversed());
  }

  @Override
  public Future<Void> store(MemoryEntry entry) {
    String filePath = memoryDir.resolve(entry.id() + ".json").toString();
    String json = Json.encodePrettily(entry);

    return vertx
        .fileSystem()
        .writeFile(filePath, io.vertx.core.buffer.Buffer.buffer(json))
        .onSuccess(
            v -> {
              // Remove existing item with same id if it exists
              index.removeIf(item -> item.id().equals(entry.id()));
              index.add(
                  new MemoryIndexItem(
                      entry.id(), entry.title(), entry.category(), entry.timestamp()));
              sortIndex();
              log.debug("Stored memory entry: {}", entry.id());
            })
        .mapEmpty();
  }

  @Override
  public Future<List<MemoryEntry>> search(MemoryQuery query) {
    // Simple hybrid search: load entries that might match, filter them
    return vertx.executeBlocking(
        () -> {
          List<MemoryEntry> results = new ArrayList<>();
          List<String> files =
              vertx.fileSystem().readDirBlocking(memoryDir.toString(), ".*\\.json");

          for (String file : files) {
            try {
              String content = vertx.fileSystem().readFileBlocking(file).toString();
              MemoryEntry entry = Json.decodeValue(content, MemoryEntry.class);

              if (matchesQuery(entry, query)) {
                results.add(entry);
              }
            } catch (Exception e) {
              log.warn("Failed to load memory entry for search from {}", file, e);
            }
          }

          // Sort by timestamp descending
          results.sort(Comparator.comparing(MemoryEntry::timestamp).reversed());

          // Apply limit
          if (query.limit() > 0 && results.size() > query.limit()) {
            results = results.subList(0, query.limit());
          }

          return results;
        });
  }

  private boolean matchesQuery(MemoryEntry entry, MemoryQuery query) {
    if (query.categories() != null && !query.categories().isEmpty()) {
      if (!query.categories().contains(entry.category())) {
        return false;
      }
    }

    if (query.tags() != null && !query.tags().isEmpty()) {
      boolean hasAllTags =
          query.tags().stream()
              .allMatch(
                  queryTag ->
                      entry.tags() != null
                          && entry.tags().stream()
                              .anyMatch(
                                  entryTag ->
                                      entryTag.name().equals(queryTag.name())
                                          && entryTag.value().equals(queryTag.value())));
      if (!hasAllTags) {
        return false;
      }
    }

    if (query.keyword() != null && !query.keyword().isBlank()) {
      String keyword = query.keyword().toLowerCase();
      boolean matchTitle = entry.title() != null && entry.title().toLowerCase().contains(keyword);
      boolean matchSummary =
          entry.summary() != null && entry.summary().toLowerCase().contains(keyword);
      boolean matchContent =
          entry.fullContent() != null && entry.fullContent().toLowerCase().contains(keyword);
      if (!matchTitle && !matchSummary && !matchContent) {
        return false;
      }
    }

    return true;
  }

  @Override
  public Future<List<MemoryIndexItem>> getRecentIndex(int limit) {
    if (limit <= 0) {
      return Future.succeededFuture(Collections.emptyList());
    }
    int size = Math.min(index.size(), limit);
    return Future.succeededFuture(new ArrayList<>(index.subList(0, size)));
  }

  @Override
  public Future<MemoryEntry> recall(String id) {
    String filePath = memoryDir.resolve(id + ".json").toString();

    return vertx
        .fileSystem()
        .readFile(filePath)
        .map(buffer -> Json.decodeValue(buffer.toString(), MemoryEntry.class))
        .recover(
            err -> Future.failedFuture(new RuntimeException("Failed to recall memory " + id, err)));
  }
}
