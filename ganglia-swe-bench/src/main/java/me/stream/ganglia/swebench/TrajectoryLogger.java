package me.stream.ganglia.swebench;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TrajectoryLogger {
    private static final Logger log = LoggerFactory.getLogger(TrajectoryLogger.class);
    private static final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    
    private final String instanceId;
    private final List<Map<String, Object>> trajectory = new ArrayList<>();
    private final File logFile;

    public TrajectoryLogger(String instanceId) {
        this.instanceId = instanceId;
        File logDir = new File("target/e2e-logs");
        if (!logDir.exists()) logDir.mkdirs();
        this.logFile = new File(logDir, instanceId + "_trajectory.json");
    }

    public void logAction(String role, String content) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("role", role);
        entry.put("content", content);
        entry.put("timestamp", System.currentTimeMillis());
        trajectory.add(entry);
        save();
    }
    
    public void logToolCall(String toolName, Map<String, Object> args, String result) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("role", "tool");
        entry.put("toolName", toolName);
        entry.put("args", args);
        entry.put("result", result != null && result.length() > 2000 ? result.substring(0, 2000) + "...(truncated)" : result);
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
