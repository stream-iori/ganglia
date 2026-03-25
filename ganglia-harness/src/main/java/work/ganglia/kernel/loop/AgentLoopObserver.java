package work.ganglia.kernel.loop;

import java.util.Map;

import work.ganglia.port.external.tool.ObservationType;
import work.ganglia.port.internal.state.TokenUsage;

/** Observer for telemetry, UI rendering, and logging events emitted by the AgentLoop. */
public interface AgentLoopObserver {
  /** Called when a specific observation event occurs during the loop. */
  void onObservation(
      String sessionId, ObservationType type, String content, Map<String, Object> data);

  /** Called when LLM token usage is reported. */
  void onUsageRecorded(String sessionId, TokenUsage usage);
}
