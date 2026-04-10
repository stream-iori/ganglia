package work.ganglia.infrastructure.internal.memory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import work.ganglia.port.internal.memory.MemoryStore;
import work.ganglia.port.internal.memory.model.MemoryCategory;
import work.ganglia.port.internal.memory.model.MemoryEntry;
import work.ganglia.port.internal.memory.model.MemoryIndexItem;
import work.ganglia.port.internal.memory.model.MemoryQuery;
import work.ganglia.port.internal.memory.model.MemoryTag;

/** SQLite-backed {@link MemoryStore} with FTS5 full-text search. */
public class SqliteMemoryStore implements MemoryStore {

  private static final Logger logger = LoggerFactory.getLogger(SqliteMemoryStore.class);

  private final SqliteConnectionManager cm;

  public SqliteMemoryStore(SqliteConnectionManager cm) {
    this.cm = cm;
  }

  @Override
  public Future<Void> store(MemoryEntry entry) {
    return cm.executeBlockingVoid(
        conn -> {
          // Use DELETE + INSERT to properly trigger FTS update/insert triggers
          try (PreparedStatement del =
              conn.prepareStatement("DELETE FROM memory_entries WHERE id = ?")) {
            del.setString(1, entry.id());
            del.executeUpdate();
          }
          String sql =
              "INSERT INTO memory_entries (id, title, summary, full_content, category, tags, related_files, created_at)"
                  + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
          try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entry.id());
            ps.setString(2, entry.title());
            ps.setString(3, entry.summary());
            ps.setString(4, entry.fullContent());
            ps.setString(5, entry.category().name());
            ps.setString(6, serializeTags(entry.tags()));
            ps.setString(7, serializeStringList(entry.relatedFiles()));
            ps.setString(8, entry.timestamp().toString());
            ps.executeUpdate();
          }
          logger.debug("Stored memory entry: {}", entry.id());
        });
  }

  @Override
  public Future<List<MemoryEntry>> search(MemoryQuery query) {
    return cm.executeBlocking(
        conn -> {
          List<MemoryEntry> results = new ArrayList<>();
          boolean hasFts = query.keyword() != null && !query.keyword().isBlank();

          StringBuilder sql = new StringBuilder();
          if (hasFts) {
            sql.append(
                "SELECT m.* FROM memory_entries m"
                    + " JOIN memory_entries_fts f ON m.rowid = f.rowid"
                    + " WHERE memory_entries_fts MATCH ?");
          } else {
            sql.append("SELECT * FROM memory_entries m WHERE 1=1");
          }

          if (query.categories() != null && !query.categories().isEmpty()) {
            String placeholders =
                query.categories().stream().map(c -> "?").collect(Collectors.joining(","));
            sql.append(" AND m.category IN (").append(placeholders).append(")");
          }

          sql.append(" ORDER BY m.created_at DESC LIMIT ?");

          try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            if (hasFts) {
              ps.setString(idx++, escapeFts(query.keyword()));
            }
            if (query.categories() != null) {
              for (MemoryCategory cat : query.categories()) {
                ps.setString(idx++, cat.name());
              }
            }
            ps.setInt(idx, query.limit() > 0 ? query.limit() : 10);

            try (ResultSet rs = ps.executeQuery()) {
              while (rs.next()) {
                MemoryEntry entry = mapEntry(rs);
                if (matchesTags(entry, query.tags())) {
                  results.add(entry);
                }
              }
            }
          }
          return results;
        });
  }

  @Override
  public Future<List<MemoryIndexItem>> getRecentIndex(int limit) {
    return cm.executeBlocking(
        conn -> {
          List<MemoryIndexItem> items = new ArrayList<>();
          String sql =
              "SELECT id, title, category, created_at FROM memory_entries"
                  + " ORDER BY created_at DESC LIMIT ?";
          try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
              while (rs.next()) {
                items.add(
                    new MemoryIndexItem(
                        rs.getString("id"),
                        rs.getString("title"),
                        parseCategory(rs.getString("category")),
                        Instant.parse(rs.getString("created_at"))));
              }
            }
          }
          return items;
        });
  }

  @Override
  public Future<MemoryEntry> recall(String id) {
    return cm.executeBlocking(
        conn -> {
          String sql = "SELECT * FROM memory_entries WHERE id = ?";
          try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
              if (rs.next()) {
                return mapEntry(rs);
              }
            }
          }
          throw new RuntimeException("Memory entry not found: " + id);
        });
  }

  private MemoryEntry mapEntry(ResultSet rs) throws java.sql.SQLException {
    return new MemoryEntry(
        rs.getString("id"),
        rs.getString("title"),
        rs.getString("summary"),
        rs.getString("full_content"),
        parseCategory(rs.getString("category")),
        deserializeTags(rs.getString("tags")),
        Instant.parse(rs.getString("created_at")),
        deserializeStringList(rs.getString("related_files")));
  }

  private MemoryCategory parseCategory(String name) {
    try {
      return MemoryCategory.valueOf(name);
    } catch (IllegalArgumentException e) {
      return MemoryCategory.UNKNOWN;
    }
  }

  private boolean matchesTags(MemoryEntry entry, List<MemoryTag> queryTags) {
    if (queryTags == null || queryTags.isEmpty()) {
      return true;
    }
    if (entry.tags() == null || entry.tags().isEmpty()) {
      return false;
    }
    return entry.tags().containsAll(queryTags);
  }

  private String escapeFts(String keyword) {
    // Wrap each word in quotes to handle special FTS characters
    String[] words = keyword.trim().split("\\s+");
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < words.length; i++) {
      if (i > 0) {
        sb.append(" ");
      }
      sb.append("\"").append(words[i].replace("\"", "\"\"")).append("\"");
    }
    return sb.toString();
  }

  static String serializeTags(List<MemoryTag> tags) {
    if (tags == null || tags.isEmpty()) {
      return "[]";
    }
    JsonArray arr = new JsonArray();
    for (MemoryTag tag : tags) {
      arr.add(new JsonObject().put("name", tag.name()).put("value", tag.value()));
    }
    return arr.encode();
  }

  static List<MemoryTag> deserializeTags(String json) {
    if (json == null || json.isBlank() || "[]".equals(json)) {
      return List.of();
    }
    JsonArray arr = new JsonArray(json);
    List<MemoryTag> tags = new ArrayList<>();
    for (int i = 0; i < arr.size(); i++) {
      JsonObject obj = arr.getJsonObject(i);
      tags.add(new MemoryTag(obj.getString("name"), obj.getString("value")));
    }
    return tags;
  }

  static String serializeStringList(List<String> list) {
    if (list == null || list.isEmpty()) {
      return "[]";
    }
    JsonArray arr = new JsonArray();
    list.forEach(arr::add);
    return arr.encode();
  }

  static List<String> deserializeStringList(String json) {
    if (json == null || json.isBlank() || "[]".equals(json)) {
      return List.of();
    }
    JsonArray arr = new JsonArray(json);
    List<String> list = new ArrayList<>();
    for (int i = 0; i < arr.size(); i++) {
      list.add(arr.getString(i));
    }
    return list;
  }
}
