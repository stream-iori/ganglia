package work.ganglia.infrastructure.internal.state;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.vertx.core.Future;

import work.ganglia.port.external.tool.ObservationType;
import work.ganglia.port.internal.state.Blackboard;
import work.ganglia.port.internal.state.ColdStorage;
import work.ganglia.port.internal.state.Fact;
import work.ganglia.port.internal.state.FactStatus;
import work.ganglia.port.internal.state.ObservationDispatcher;

/**
 * In-memory Blackboard adapter using ConcurrentHashMap with optimistic versioning. Suitable for
 * single-JVM deployments. Optionally dispatches observations for trace integration.
 */
public class InMemoryBlackboard implements Blackboard {

  private final ConcurrentHashMap<String, Fact> facts = new ConcurrentHashMap<>();
  private final AtomicInteger idCounter = new AtomicInteger(0);
  private final ObservationDispatcher dispatcher;
  private final String sessionId;
  private final ColdStorage coldStorage;

  /** Creates a Blackboard without observation dispatch (for testing). */
  public InMemoryBlackboard() {
    this(null, null, null);
  }

  /** Creates a Blackboard with observation dispatch for trace integration. */
  public InMemoryBlackboard(ObservationDispatcher dispatcher, String sessionId) {
    this(dispatcher, sessionId, null);
  }

  /** Creates a Blackboard with observation dispatch and L2 cold storage. */
  public InMemoryBlackboard(
      ObservationDispatcher dispatcher, String sessionId, ColdStorage coldStorage) {
    this.dispatcher = dispatcher;
    this.sessionId = sessionId;
    this.coldStorage = coldStorage;
  }

  @Override
  public Future<Fact> publish(
      String managerId,
      String summary,
      String detailRef,
      int cycleNumber,
      Map<String, String> tags) {
    String id = "fact-" + idCounter.incrementAndGet();
    Map<String, String> safeTags = tags != null ? Collections.unmodifiableMap(tags) : Map.of();
    Fact fact =
        new Fact(
            id,
            1,
            summary,
            detailRef,
            managerId,
            FactStatus.ACTIVE,
            Instant.now(),
            cycleNumber,
            safeTags);
    facts.put(id, fact);
    dispatchFactEvent(ObservationType.FACT_PUBLISHED, fact, null);
    return Future.succeededFuture(fact);
  }

  @Override
  public Future<Void> supersede(String factId, String reason) {
    Fact existing = facts.get(factId);
    if (existing == null) {
      return Future.failedFuture("Fact not found: " + factId);
    }
    Fact superseded =
        new Fact(
            existing.id(),
            existing.version() + 1,
            existing.summary(),
            existing.detailRef(),
            existing.sourceManager(),
            FactStatus.SUPERSEDED,
            existing.createdAt(),
            existing.cycleNumber(),
            existing.tags());
    if (!facts.replace(factId, existing, superseded)) {
      return Future.failedFuture("Concurrent modification on fact: " + factId);
    }
    dispatchFactEvent(ObservationType.FACT_SUPERSEDED, superseded, reason);
    return Future.succeededFuture();
  }

  @Override
  public Future<List<Fact>> getActiveFacts() {
    List<Fact> active =
        facts.values().stream().filter(f -> f.status() == FactStatus.ACTIVE).toList();
    return Future.succeededFuture(active);
  }

  @Override
  public Future<String> getFactDetail(String factId) {
    Fact fact = facts.get(factId);
    if (fact == null || fact.detailRef() == null) {
      return Future.succeededFuture(null);
    }
    if (coldStorage != null) {
      return coldStorage.read(fact.detailRef());
    }
    return Future.succeededFuture(fact.detailRef());
  }

  /**
   * Publishes a fact with detail content written to L2 cold storage. If cold storage is configured
   * and both detailRef and detailContent are provided, the content is written before publishing.
   */
  public Future<Fact> publishWithDetail(
      String managerId, String summary, String detailRef, String detailContent, int cycleNumber) {
    if (coldStorage != null && detailRef != null && detailContent != null) {
      return coldStorage
          .write(detailRef, detailContent)
          .compose(v -> publish(managerId, summary, detailRef, cycleNumber));
    }
    return publish(managerId, summary, detailRef, cycleNumber);
  }

  @Override
  public Future<Integer> getSupersededCount() {
    int count =
        (int) facts.values().stream().filter(f -> f.status() == FactStatus.SUPERSEDED).count();
    return Future.succeededFuture(count);
  }

  @Override
  public Future<Integer> getNewFactCount(int lastNCycles) {
    int maxCycle = facts.values().stream().mapToInt(Fact::cycleNumber).max().orElse(0);
    int minCycle = maxCycle - lastNCycles + 1;
    int count =
        (int)
            facts.values().stream()
                .filter(f -> f.status() == FactStatus.ACTIVE && f.cycleNumber() >= minCycle)
                .count();
    return Future.succeededFuture(count);
  }

  @Override
  public Future<List<Fact>> getSupersededFacts() {
    List<Fact> superseded =
        facts.values().stream().filter(f -> f.status() == FactStatus.SUPERSEDED).toList();
    return Future.succeededFuture(superseded);
  }

  @Override
  public Future<Void> archive(String factId) {
    Fact existing = facts.get(factId);
    if (existing == null) {
      return Future.failedFuture("Fact not found: " + factId);
    }
    if (existing.status() != FactStatus.SUPERSEDED) {
      return Future.failedFuture(
          "Can only archive SUPERSEDED facts, fact " + factId + " is " + existing.status());
    }
    Fact archived =
        new Fact(
            existing.id(),
            existing.version() + 1,
            existing.summary(),
            existing.detailRef(),
            existing.sourceManager(),
            FactStatus.ARCHIVED,
            existing.createdAt(),
            existing.cycleNumber(),
            existing.tags());
    if (!facts.replace(factId, existing, archived)) {
      return Future.failedFuture("Concurrent modification on fact: " + factId);
    }
    dispatchFactEvent(ObservationType.FACT_ARCHIVED, archived, "Archived after summarization");
    return Future.succeededFuture();
  }

  private void dispatchFactEvent(ObservationType type, Fact fact, String reason) {
    if (dispatcher == null || sessionId == null) {
      return;
    }
    Map<String, Object> data = new HashMap<>();
    data.put("factId", fact.id());
    data.put("version", fact.version());
    data.put("summary", fact.summary());
    data.put("sourceManager", fact.sourceManager());
    data.put("status", fact.status().name());
    data.put("cycleNumber", fact.cycleNumber());
    if (!fact.tags().isEmpty()) {
      data.put("tags", fact.tags());
    }
    if (fact.detailRef() != null) {
      data.put("detailRef", fact.detailRef());
    }
    if (reason != null) {
      data.put("reason", reason);
    }
    dispatcher.dispatch(sessionId, type, fact.summary(), data);
  }
}
