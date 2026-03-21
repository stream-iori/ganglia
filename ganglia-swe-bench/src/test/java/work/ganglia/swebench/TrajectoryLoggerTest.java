package work.ganglia.swebench;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class TrajectoryLoggerTest {
  @Test
  void testLogSaving(@TempDir Path tempDir) {
    // We manually point to target/e2e-logs in the class, so we just test if it creates files
    TrajectoryLogger logger = new TrajectoryLogger("test-instance");
    logger.logAction("system", "Test start");
    logger.logToolCall("ls", Map.of("path", "/"), "file1\nfile2");

    File logFile = new File("target/e2e-logs/test-instance_trajectory.json");
    assertTrue(logFile.exists(), "Trajectory log file should be created");
  }
}
