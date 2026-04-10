package work.ganglia.kernel.subagent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;

/** Computes a deterministic fingerprint for a task node based on its inputs. */
public final class TaskFingerprint {
  private TaskFingerprint() {}

  /**
   * Computes a SHA-256 hex fingerprint from a task description, persona, and dependency results.
   * Dependency results are sorted by key to ensure deterministic ordering.
   */
  public static String compute(String task, String persona, Map<String, String> dependencyResults) {
    StringBuilder sb = new StringBuilder();
    sb.append(task).append('|');
    sb.append(persona != null ? persona : "").append('|');

    // Sort dependency results by key for deterministic hashing
    TreeMap<String, String> sorted = new TreeMap<>(dependencyResults);
    sorted.forEach((key, value) -> sb.append(key).append('=').append(value).append(';'));

    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
      return bytesToHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-256 not available", e);
    }
  }

  private static String bytesToHex(byte[] bytes) {
    StringBuilder hex = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      hex.append(String.format("%02x", b));
    }
    return hex.toString();
  }
}
