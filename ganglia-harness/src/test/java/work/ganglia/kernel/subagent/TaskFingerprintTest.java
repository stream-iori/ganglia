package work.ganglia.kernel.subagent;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

class TaskFingerprintTest {

  @Test
  void compute_sameInputs_produceSameHash() {
    Map<String, String> deps = Map.of("dep1", "result-A", "dep2", "result-B");
    String hash1 = TaskFingerprint.compute("Do task", "GENERAL", deps);
    String hash2 = TaskFingerprint.compute("Do task", "GENERAL", deps);
    assertEquals(hash1, hash2);
  }

  @Test
  void compute_differentTask_producesDifferentHash() {
    Map<String, String> deps = Map.of("dep1", "result-A");
    String hash1 = TaskFingerprint.compute("Task A", "GENERAL", deps);
    String hash2 = TaskFingerprint.compute("Task B", "GENERAL", deps);
    assertNotEquals(hash1, hash2);
  }

  @Test
  void compute_differentDependencyResults_producesDifferentHash() {
    String hash1 = TaskFingerprint.compute("Do task", "GENERAL", Map.of("dep1", "result-A"));
    String hash2 = TaskFingerprint.compute("Do task", "GENERAL", Map.of("dep1", "result-B"));
    assertNotEquals(hash1, hash2);
  }

  @Test
  void compute_differentPersona_producesDifferentHash() {
    Map<String, String> deps = Map.of("dep1", "result-A");
    String hash1 = TaskFingerprint.compute("Do task", "GENERAL", deps);
    String hash2 = TaskFingerprint.compute("Do task", "INVESTIGATOR", deps);
    assertNotEquals(hash1, hash2);
  }

  @Test
  void compute_nullPersona_handled() {
    Map<String, String> deps = Map.of("dep1", "result-A");
    String hash = TaskFingerprint.compute("Do task", null, deps);
    assertNotNull(hash);
    assertFalse(hash.isEmpty());
  }

  @Test
  void compute_emptyDependencies_producesValidHash() {
    String hash = TaskFingerprint.compute("Do task", "GENERAL", Collections.emptyMap());
    assertNotNull(hash);
    assertFalse(hash.isEmpty());
  }

  @Test
  void compute_hashIsDeterministicRegardlessOfMapOrder() {
    // TreeMap guarantees sorted order; HashMap iteration order is undefined
    TreeMap<String, String> sorted = new TreeMap<>();
    sorted.put("dep2", "result-B");
    sorted.put("dep1", "result-A");

    String hash1 = TaskFingerprint.compute("Do task", "GENERAL", sorted);
    String hash2 =
        TaskFingerprint.compute(
            "Do task", "GENERAL", Map.of("dep1", "result-A", "dep2", "result-B"));
    assertEquals(hash1, hash2);
  }
}
