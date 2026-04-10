package work.ganglia.infrastructure.external.tool;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.vertx.core.Future;

import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ToolDefinition;
import work.ganglia.port.external.tool.ToolSet;
import work.ganglia.port.external.tool.model.ToolInvokeResult;
import work.ganglia.port.internal.memory.SessionStore;
import work.ganglia.port.internal.memory.model.SessionRecord;
import work.ganglia.port.internal.memory.model.SessionSummary;
import work.ganglia.port.internal.state.ExecutionContext;

/** Agent-callable tools for cross-session search and retrieval. */
public class SessionSearchTools implements ToolSet {
  private static final int DEFAULT_LIMIT = 10;

  private final SessionStore sessionStore;

  public SessionSearchTools(SessionStore sessionStore) {
    this.sessionStore = sessionStore;
  }

  @Override
  public List<ToolDefinition> getDefinitions() {
    return List.of(
        new ToolDefinition(
            "search_sessions",
            "Search past session records by keyword. Returns matching sessions with their goals and summaries.",
            """
                {
                  "type": "object",
                  "properties": {
                    "query": {
                      "type": "string",
                      "description": "Search keywords to match against session goals and summaries"
                    },
                    "limit": {
                      "type": "integer",
                      "description": "Maximum number of results (default 10)",
                      "default": 10
                    }
                  },
                  "required": ["query"]
                }
                """),
        new ToolDefinition(
            "get_session",
            "Retrieve the full details of a specific past session by its ID.",
            """
                {
                  "type": "object",
                  "properties": {
                    "session_id": {
                      "type": "string",
                      "description": "The session ID to retrieve"
                    }
                  },
                  "required": ["session_id"]
                }
                """));
  }

  @Override
  public Future<ToolInvokeResult> execute(
      String toolName, Map<String, Object> args, SessionContext context, ExecutionContext ec) {
    return switch (toolName) {
      case "search_sessions" -> searchSessions(args);
      case "get_session" -> getSession(args);
      default -> Future.succeededFuture(ToolInvokeResult.error("Unknown tool: " + toolName));
    };
  }

  private Future<ToolInvokeResult> searchSessions(Map<String, Object> args) {
    String query = (String) args.get("query");
    if (query == null || query.isBlank()) {
      return Future.succeededFuture(ToolInvokeResult.error("'query' is required"));
    }
    int limit = args.containsKey("limit") ? ((Number) args.get("limit")).intValue() : DEFAULT_LIMIT;

    return sessionStore
        .searchSessions(query, limit)
        .map(
            results -> {
              if (results.isEmpty()) {
                return ToolInvokeResult.success("No sessions found matching '" + query + "'.");
              }
              String listing =
                  results.stream()
                      .map(SessionSearchTools::formatSummary)
                      .collect(Collectors.joining("\n\n"));
              return ToolInvokeResult.success(
                  "Found " + results.size() + " session(s):\n\n" + listing);
            })
        .recover(
            err ->
                Future.succeededFuture(
                    ToolInvokeResult.error("Search failed: " + err.getMessage())));
  }

  private Future<ToolInvokeResult> getSession(Map<String, Object> args) {
    String sessionId = (String) args.get("session_id");
    if (sessionId == null || sessionId.isBlank()) {
      return Future.succeededFuture(ToolInvokeResult.error("'session_id' is required"));
    }

    return sessionStore
        .getSession(sessionId)
        .map(SessionSearchTools::formatRecord)
        .map(ToolInvokeResult::success)
        .recover(
            err ->
                Future.succeededFuture(
                    ToolInvokeResult.error(
                        "Failed to get session '" + sessionId + "': " + err.getMessage())));
  }

  private static String formatSummary(SessionSummary s) {
    return "- **"
        + s.sessionId()
        + "** ("
        + s.startTime()
        + ")\n  Goal: "
        + s.goal()
        + "\n  Match: "
        + s.matchSnippet();
  }

  private static String formatRecord(SessionRecord r) {
    return "Session: "
        + r.sessionId()
        + "\nGoal: "
        + r.goal()
        + "\nSummary: "
        + r.summary()
        + "\nTurns: "
        + r.turnCount()
        + ", Tool calls: "
        + r.toolCallCount()
        + "\nStart: "
        + r.startTime()
        + "\nEnd: "
        + r.endTime();
  }
}
