package work.ganglia.web.model;

import java.util.List;

/** Generic event sent from server to client. */
public record ServerEvent(String eventId, long timestamp, EventType type, Object data) {
  // Nested data records for various event types

  public record ThoughtData(String content) {}

  public record ToolStartData(String toolCallId, String toolName, String command) {}

  public record ToolOutputStreamData(String toolCallId, String text, boolean isError) {}

  public record ToolResultData(
      String toolCallId,
      int exitCode,
      String summary,
      String fullOutput,
      boolean isError,
      String errorType) {}

  public record AskUserData(
      String askId, String question, List<AskOption> options, String diffContext) {}

  public record AskOption(String value, String label, String description) {}

  public record AgentMessageData(String content) {}

  public record UserMessageData(String content) {}

  public record SystemErrorData(String code, String message, String stackTrace, boolean canRetry) {}

  public record FileContentData(String path, String content, String language) {}

  public record TokenData(String content) {}

  public record InitConfigData(String workspacePath, String sessionId, int mcpCount) {}

  public record PlanUpdateData(work.ganglia.kernel.todo.ToDoList plan) {}
}
