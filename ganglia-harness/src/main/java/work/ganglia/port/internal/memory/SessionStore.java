package work.ganglia.port.internal.memory;

import java.util.List;

import io.vertx.core.Future;

import work.ganglia.port.internal.memory.model.SessionRecord;
import work.ganglia.port.internal.memory.model.SessionSummary;

/**
 * Store for persisting and searching session records. Enables cross-session knowledge retrieval.
 */
public interface SessionStore {

  /**
   * Saves a completed session record.
   *
   * @param record The session record to persist.
   * @return A Future completing when the record is saved.
   */
  Future<Void> saveSession(SessionRecord record);

  /**
   * Searches sessions by keyword query.
   *
   * @param query The search query (keywords matched against goal and summary).
   * @param limit Maximum number of results to return.
   * @return A Future with matching session summaries, ordered by recency.
   */
  Future<List<SessionSummary>> searchSessions(String query, int limit);

  /**
   * Retrieves a specific session record by ID.
   *
   * @param sessionId The session identifier.
   * @return A Future with the session record, or a failed future if not found.
   */
  Future<SessionRecord> getSession(String sessionId);
}
