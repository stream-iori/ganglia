package work.ganglia.swebench;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import work.ganglia.kernel.loop.AgentLoopObserver;
import work.ganglia.port.external.tool.ObservationType;
import work.ganglia.port.internal.state.TokenUsage;

public class TrajectoryLogger implements AgentLoopObserver {
  private static final Logger log = LoggerFactory.getLogger(TrajectoryLogger.class);
  private static final ObjectMapper mapper =
      new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

  private final String instanceId;
  private final List<Map<String, Object>> trajectory = new ArrayList<>();
  private final File logFile;

  public TrajectoryLogger(String instanceId) {
    this.instanceId = instanceId;
    File logDir = new File("target/e2e-logs");
    if (!logDir.exists()) {
      logDir.mkdirs();
    }
    this.logFile = new File(logDir, instanceId + "_trajectory.json");
  }

  @Override
  public void onObservation(
      String sessionId, ObservationType type, String content, Map<String, Object> data) {
    Map<String, Object> entry = new HashMap<>();
    entry.put("type", type.name());
    entry.put(
        "content",
        content != null && content.length() > 4000
            ? content.substring(0, 4000) + "...(truncated)"
            : content);
    if (data != null) {
      entry.put("data", data);
    }
    entry.put("timestamp", System.currentTimeMillis());
    trajectory.add(entry);
    save();
  }

  @Override
  public void onUsageRecorded(String sessionId, TokenUsage usage) {
    Map<String, Object> entry = new HashMap<>();
    entry.put("type", "TOKEN_USAGE");
    entry.put("usage", usage);
    entry.put("timestamp", System.currentTimeMillis());
    trajectory.add(entry);
    save();
  }

  public void logAction(String role, String content) {
    Map<String, Object> entry = new HashMap<>();
    entry.put("role", role);
    entry.put("content", content);
    entry.put("timestamp", System.currentTimeMillis());
    trajectory.add(entry);
    save();
  }

  public void logToolCall(String name, Map<String, String> args, String result) {
    Map<String, Object> entry = new HashMap<>();
    entry.put("type", "TOOL_CALL");
    entry.put("name", name);
    entry.put("arguments", args);
    entry.put("result", result);
    entry.put("timestamp", System.currentTimeMillis());
    trajectory.add(entry);
    save();
  }

  private void save() {
    try {
      mapper.writeValue(logFile, trajectory);
    } catch (IOException e) {
      log.error("Failed to save trajectory for {}", instanceId, e);
    }
  }
}
