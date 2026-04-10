package work.ganglia.trajectory.event;

/** Data records shared by all agent UIs (coding, trading, etc.). */
public final class CommonEventData {
  private CommonEventData() {}

  public record ThoughtData(String content) {}

  public record ToolStartData(String toolCallId, String toolName, String command) {}

  public record ToolResultData(
      String toolCallId, int exitCode, String summary, String fullOutput, boolean isError) {}

  public record TokenData(String content) {}

  public record AgentMessageData(String content) {}

  public record UserMessageData(String content) {}

  public record SystemErrorData(String code, String message, String stackTrace, boolean canRetry) {}

  public record SessionStartedData(String sessionId, String firstPrompt) {}

  public record SessionEndedData(String sessionId, long durationMs) {}
}
