package work.ganglia.web.model;

import java.util.List;

import work.ganglia.trajectory.event.EventType;

/** Generic event sent from server to client. */
public record ServerEvent(String eventId, long timestamp, EventType type, Object data) {
  // Coding-specific data records only — common records live in CommonEventData

  public record ToolOutputStreamData(String toolCallId, String text, boolean isError) {}

  public record AskUserData(String askId, List<AskUserQuestion> questions, String diffContext) {}

  public record AskUserQuestion(
      String question,
      String header,
      String type,
      List<AskOption> options,
      boolean multiSelect,
      String placeholder) {}

  public record AskOption(String value, String label, String description) {}

  public record FileContentData(String path, String content, String language) {}

  public record InitConfigData(String workspacePath, String sessionId, int mcpCount) {}

  public record PlanUpdateData(work.ganglia.kernel.todo.ToDoList plan) {}
}
