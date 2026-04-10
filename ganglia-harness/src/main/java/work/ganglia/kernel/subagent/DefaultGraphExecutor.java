package work.ganglia.kernel.subagent;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;

import work.ganglia.kernel.loop.AgentLoop;
import work.ganglia.kernel.loop.AgentLoopFactory;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ObservationType;
import work.ganglia.port.internal.state.Blackboard;
import work.ganglia.port.internal.state.Fact;
import work.ganglia.port.internal.state.ObservationDispatcher;
import work.ganglia.port.internal.worktree.WorktreeHandle;
import work.ganglia.port.internal.worktree.WorktreeManager;

/**
 * SRP: Orchestrates the parallel/sequential execution of a TaskGraph. Uses AgentLoopFactory for
 * individual node execution. Supports worktree isolation for parallel write tasks.
 */
public class DefaultGraphExecutor implements GraphExecutor {
  private static final Logger logger = LoggerFactory.getLogger(DefaultGraphExecutor.class);

  private record CachedResult(String result, Instant cachedAt) {}

  private final AgentLoopFactory loopFactory;
  private final WorktreeManager worktreeManager;
  private final ObservationDispatcher dispatcher;
  private final Duration cacheTtl;
  private final Blackboard blackboard;
  private final ConcurrentHashMap<String, CachedResult> fingerprintCache =
      new ConcurrentHashMap<>();
  private final CopyOnWriteArrayList<WorktreeHandle> worktreeHandles = new CopyOnWriteArrayList<>();

  public DefaultGraphExecutor(AgentLoopFactory loopFactory) {
    this(loopFactory, null, null, null, null);
  }

  public DefaultGraphExecutor(AgentLoopFactory loopFactory, WorktreeManager worktreeManager) {
    this(loopFactory, worktreeManager, null, null, null);
  }

  public DefaultGraphExecutor(
      AgentLoopFactory loopFactory,
      WorktreeManager worktreeManager,
      ObservationDispatcher dispatcher,
      Duration cacheTtl) {
    this(loopFactory, worktreeManager, dispatcher, cacheTtl, null);
  }

  public DefaultGraphExecutor(
      AgentLoopFactory loopFactory,
      WorktreeManager worktreeManager,
      ObservationDispatcher dispatcher,
      Duration cacheTtl,
      Blackboard blackboard) {
    this.loopFactory = loopFactory;
    this.worktreeManager = worktreeManager;
    this.dispatcher = dispatcher;
    this.cacheTtl = cacheTtl;
    this.blackboard = blackboard;
  }

  /** Returns the worktree handles created during execution, for use by MergeGate. */
  public List<WorktreeHandle> getWorktreeHandles() {
    return Collections.unmodifiableList(new ArrayList<>(worktreeHandles));
  }

  @Override
  public Future<String> execute(TaskGraph graph, SessionContext parentContext) {
    logger.info(
        "Executing task graph with {} nodes for session: {}",
        graph.nodes().size(),
        parentContext.sessionId());

    worktreeHandles.clear();
    Map<String, String> results = new HashMap<>();
    int[] completedCount = {0};

    return executeReadyNodes(graph, parentContext, results, completedCount);
  }

  private Future<String> executeReadyNodes(
      TaskGraph graph,
      SessionContext parentContext,
      Map<String, String> results,
      int[] completedCount) {
    var readyNodes =
        graph.nodes().stream()
            .filter(
                n -> !results.containsKey(n.id()) && results.keySet().containsAll(n.dependencies()))
            .toList();

    if (readyNodes.isEmpty()) {
      if (completedCount[0] >= graph.nodes().size()) {
        return Future.succeededFuture(formatFinalReport(graph, results));
      }
      return Future.failedFuture("Graph deadlock or cycle detected.");
    }

    var nodeFutures =
        readyNodes.stream().map(node -> executeNode(node, parentContext, results)).toList();

    return Future.all(nodeFutures)
        .compose(
            v -> {
              completedCount[0] += readyNodes.size();
              return executeReadyNodes(graph, parentContext, results, completedCount);
            });
  }

  private Future<Void> executeNode(
      TaskNode node, SessionContext parentContext, Map<String, String> results) {
    if (node.isolation() == IsolationLevel.WORKTREE && worktreeManager != null) {
      return createWorktreeAndExecute(node, parentContext, results);
    }
    return executeNodeDirect(node, parentContext, results, null);
  }

  private Future<Void> createWorktreeAndExecute(
      TaskNode node, SessionContext parentContext, Map<String, String> results) {
    return worktreeManager
        .create(node.id())
        .compose(
            handle -> {
              worktreeHandles.add(handle);
              logger.info("Created worktree for node [{}]: {}", node.id(), handle.branchName());
              return executeNodeDirect(node, parentContext, results, handle);
            });
  }

  private Future<Void> executeNodeDirect(
      TaskNode node,
      SessionContext parentContext,
      Map<String, String> results,
      WorktreeHandle worktreeHandle) {
    logger.info("Starting graph node [{}]: {}", node.id(), node.task());

    String sessionId = parentContext.sessionId() + "-node-" + node.id();
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("graph_node_id", node.id());
    metadata.put("sub_agent_persona", node.persona());

    // Propagate mission context to child metadata
    if (node.missionContext() != null) {
      metadata.put("mission_context", node.missionContext());
    }

    // Record worktree info in metadata for downstream use
    if (worktreeHandle != null) {
      metadata.put("worktree_branch", worktreeHandle.branchName());
      metadata.put("worktree_path", worktreeHandle.worktreePath().toString());
    }

    SessionContext nodeContext = ContextScoper.scope(sessionId, parentContext, metadata);

    AgentLoop childLoop = loopFactory.createLoop();

    // Build prompt: optional MISSION prefix, then TASK, then dependency results
    StringBuilder promptBuilder = new StringBuilder();
    if (node.missionContext() != null) {
      promptBuilder.append("MISSION: ").append(node.missionContext()).append("\n\n");
    }
    promptBuilder.append("TASK: ").append(node.task()).append("\n\n");

    // Resolve dependency results: use inputMapping if provided, otherwise include all
    Map<String, String> dependencyResults;
    if (node.inputMapping() != null && !node.inputMapping().isEmpty()) {
      dependencyResults = new HashMap<>();
      node.inputMapping().forEach((key, depId) -> dependencyResults.put(key, results.get(depId)));
    } else {
      dependencyResults = new HashMap<>();
      node.dependencies().forEach(depId -> dependencyResults.put(depId, results.get(depId)));
    }

    if (!dependencyResults.isEmpty()) {
      promptBuilder.append("PREVIOUS TASK RESULTS:\n");
      dependencyResults.forEach(
          (key, report) -> {
            promptBuilder.append("ID: ").append(key).append("\n");
            promptBuilder.append("RESULT:\n").append(report).append("\n");
          });
    }

    // Inject Blackboard active facts from previous cycles (if Blackboard is wired)
    Future<Void> factInjection;
    if (blackboard != null) {
      factInjection =
          blackboard
              .getActiveFacts()
              .map(
                  activeFacts -> {
                    if (!activeFacts.isEmpty()) {
                      promptBuilder.append("\nESTABLISHED FINDINGS FROM PREVIOUS CYCLES:\n");
                      for (Fact fact : activeFacts) {
                        promptBuilder
                            .append("- [Cycle ")
                            .append(fact.cycleNumber())
                            .append(", Source: ")
                            .append(fact.sourceManager());
                        if (!fact.tags().isEmpty()) {
                          promptBuilder.append(", Tags: ").append(fact.tags());
                        }
                        promptBuilder.append("] ").append(fact.summary()).append("\n");
                      }
                    }
                    return (Void) null;
                  });
    } else {
      factInjection = Future.succeededFuture();
    }

    return factInjection.compose(
        v -> {
          // Fingerprint caching: skip execution if cached result is still valid
          if (cacheTtl != null) {
            String fingerprint =
                TaskFingerprint.compute(node.task(), node.persona(), dependencyResults);
            CachedResult cached = fingerprintCache.get(fingerprint);
            if (cached != null
                && !Duration.between(cached.cachedAt(), Instant.now()).isNegative()
                && Duration.between(cached.cachedAt(), Instant.now()).compareTo(cacheTtl) < 0) {
              logger.info("Fingerprint cache HIT for node [{}], skipping execution", node.id());
              results.put(node.id(), cached.result());
              dispatchCacheEvent(
                  sessionId, ObservationType.FINGERPRINT_CACHE_HIT, node.id(), fingerprint);
              return Future.succeededFuture(null);
            }
            logger.info("Fingerprint cache MISS for node [{}]", node.id());
            dispatchCacheEvent(
                sessionId, ObservationType.FINGERPRINT_CACHE_MISS, node.id(), fingerprint);

            return childLoop
                .run(promptBuilder.toString(), nodeContext)
                .map(
                    report -> {
                      results.put(node.id(), report);
                      fingerprintCache.put(fingerprint, new CachedResult(report, Instant.now()));
                      return (Void) null;
                    });
          }

          return childLoop
              .run(promptBuilder.toString(), nodeContext)
              .map(
                  report -> {
                    results.put(node.id(), report);
                    return (Void) null;
                  });
        });
  }

  private void dispatchCacheEvent(
      String sessionId, ObservationType type, String nodeId, String fingerprint) {
    if (dispatcher == null) {
      return;
    }
    Map<String, Object> data = new HashMap<>();
    data.put("nodeId", nodeId);
    data.put("fingerprint", fingerprint);
    dispatcher.dispatch(sessionId, type, "Node " + nodeId, data);
  }

  private String formatFinalReport(TaskGraph graph, Map<String, String> results) {
    StringBuilder sb = new StringBuilder("# Task Graph Execution Report\n\n");
    graph
        .nodes()
        .forEach(
            node -> {
              sb.append("## NODE ID: ").append(node.id()).append("\n");
              sb.append("### TASK: ").append(node.task()).append("\n");
              sb.append("### RESULT:\n").append(results.get(node.id())).append("\n\n");
            });
    return sb.toString();
  }
}
