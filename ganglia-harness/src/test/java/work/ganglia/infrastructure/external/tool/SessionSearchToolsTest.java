package work.ganglia.infrastructure.external.tool;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.Future;

import work.ganglia.port.external.tool.ToolDefinition;
import work.ganglia.port.external.tool.model.ToolInvokeResult;
import work.ganglia.port.internal.memory.SessionStore;
import work.ganglia.port.internal.memory.model.SessionRecord;
import work.ganglia.port.internal.memory.model.SessionSummary;
import work.ganglia.stubs.StubExecutionContext;

class SessionSearchToolsTest {

  private SessionSearchTools tools;
  private InMemorySessionStore store;
  private StubExecutionContext execCtx;

  @BeforeEach
  void setUp() {
    this.store = new InMemorySessionStore();
    this.tools = new SessionSearchTools(store);
    this.execCtx = new StubExecutionContext();
  }

  @Test
  void getDefinitions_returnsTwoTools() {
    List<ToolDefinition> defs = tools.getDefinitions();
    assertEquals(2, defs.size());
    List<String> names = defs.stream().map(ToolDefinition::name).toList();
    assertTrue(names.contains("search_sessions"));
    assertTrue(names.contains("get_session"));
  }

  @Test
  void searchSessions_returnsResults() {
    Instant now = Instant.now();
    store.sessions.add(
        new SessionRecord("s1", "Fix auth bug", "Fixed JWT validation", 5, 12, now, now));

    Map<String, Object> args = Map.of("query", "auth");
    Future<ToolInvokeResult> future = tools.execute("search_sessions", args, null, execCtx);
    ToolInvokeResult result = future.result();

    assertEquals(ToolInvokeResult.Status.SUCCESS, result.status());
    assertTrue(result.output().contains("1 session(s)"));
    assertTrue(result.output().contains("s1"));
  }

  @Test
  void searchSessions_noResults() {
    Map<String, Object> args = Map.of("query", "nonexistent");
    Future<ToolInvokeResult> future = tools.execute("search_sessions", args, null, execCtx);
    ToolInvokeResult result = future.result();

    assertEquals(ToolInvokeResult.Status.SUCCESS, result.status());
    assertTrue(result.output().contains("No sessions found"));
  }

  @Test
  void searchSessions_missingQuery_returnsError() {
    Map<String, Object> args = Map.of();
    Future<ToolInvokeResult> future = tools.execute("search_sessions", args, null, execCtx);
    ToolInvokeResult result = future.result();

    assertEquals(ToolInvokeResult.Status.ERROR, result.status());
    assertTrue(result.output().contains("query"));
  }

  @Test
  void searchSessions_withLimit() {
    Instant now = Instant.now();
    store.sessions.add(new SessionRecord("s1", "Task one", "done", 1, 1, now, now));
    store.sessions.add(new SessionRecord("s2", "Task two", "done", 2, 2, now, now));
    store.sessions.add(new SessionRecord("s3", "Task three", "done", 3, 3, now, now));

    Map<String, Object> args = new HashMap<>();
    args.put("query", "Task");
    args.put("limit", 2);
    Future<ToolInvokeResult> future = tools.execute("search_sessions", args, null, execCtx);
    ToolInvokeResult result = future.result();

    assertEquals(ToolInvokeResult.Status.SUCCESS, result.status());
    assertTrue(result.output().contains("2 session(s)"));
  }

  @Test
  void getSession_found() {
    Instant start = Instant.parse("2026-04-10T06:00:00Z");
    Instant end = Instant.parse("2026-04-10T06:30:00Z");
    store.sessions.add(new SessionRecord("s1", "Fix auth", "Fixed JWT", 5, 12, start, end));

    Map<String, Object> args = Map.of("session_id", "s1");
    Future<ToolInvokeResult> future = tools.execute("get_session", args, null, execCtx);
    ToolInvokeResult result = future.result();

    assertEquals(ToolInvokeResult.Status.SUCCESS, result.status());
    assertTrue(result.output().contains("Fix auth"));
    assertTrue(result.output().contains("Fixed JWT"));
    assertTrue(result.output().contains("Turns: 5"));
  }

  @Test
  void getSession_notFound() {
    Map<String, Object> args = Map.of("session_id", "nonexistent");
    Future<ToolInvokeResult> future = tools.execute("get_session", args, null, execCtx);
    ToolInvokeResult result = future.result();

    assertEquals(ToolInvokeResult.Status.ERROR, result.status());
    assertTrue(result.output().contains("nonexistent"));
  }

  @Test
  void getSession_missingId_returnsError() {
    Map<String, Object> args = Map.of();
    Future<ToolInvokeResult> future = tools.execute("get_session", args, null, execCtx);
    ToolInvokeResult result = future.result();

    assertEquals(ToolInvokeResult.Status.ERROR, result.status());
    assertTrue(result.output().contains("session_id"));
  }

  @Test
  void unknownTool_returnsError() {
    Future<ToolInvokeResult> future = tools.execute("unknown", Map.of(), null, execCtx);
    ToolInvokeResult result = future.result();
    assertEquals(ToolInvokeResult.Status.ERROR, result.status());
  }

  /** Simple in-memory SessionStore for unit testing. */
  private static class InMemorySessionStore implements SessionStore {
    final List<SessionRecord> sessions = new ArrayList<>();

    @Override
    public Future<Void> saveSession(SessionRecord record) {
      sessions.removeIf(r -> r.sessionId().equals(record.sessionId()));
      sessions.add(record);
      return Future.succeededFuture();
    }

    @Override
    public Future<List<SessionSummary>> searchSessions(String query, int limit) {
      String lower = query.toLowerCase();
      List<SessionSummary> results = new ArrayList<>();
      for (int i = sessions.size() - 1; i >= 0 && results.size() < limit; i--) {
        SessionRecord r = sessions.get(i);
        if (r.goal().toLowerCase().contains(lower) || r.summary().toLowerCase().contains(lower)) {
          results.add(new SessionSummary(r.sessionId(), r.goal(), r.goal(), r.startTime()));
        }
      }
      return Future.succeededFuture(results);
    }

    @Override
    public Future<SessionRecord> getSession(String sessionId) {
      return sessions.stream()
          .filter(r -> r.sessionId().equals(sessionId))
          .findFirst()
          .map(Future::succeededFuture)
          .orElse(Future.failedFuture("Session not found: " + sessionId));
    }
  }
}
