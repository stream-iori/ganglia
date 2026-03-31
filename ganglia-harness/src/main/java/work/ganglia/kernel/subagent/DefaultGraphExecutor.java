package work.ganglia.kernel.subagent;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;

import work.ganglia.kernel.loop.AgentLoop;
import work.ganglia.kernel.loop.AgentLoopFactory;
import work.ganglia.port.chat.SessionContext;

/**
 * SRP: Orchestrates the parallel/sequential execution of a TaskGraph. Uses AgentLoopFactory for
 * individual node execution.
 */
public class DefaultGraphExecutor implements GraphExecutor {
  private static final Logger logger = LoggerFactory.getLogger(DefaultGraphExecutor.class);

  private final AgentLoopFactory loopFactory;

  public DefaultGraphExecutor(AgentLoopFactory loopFactory) {
    this.loopFactory = loopFactory;
  }

  @Override
  public Future<String> execute(TaskGraph graph, SessionContext parentContext) {
    logger.info(
        "Executing task graph with {} nodes for session: {}",
        graph.nodes().size(),
        parentContext.sessionId());

    Map<String, String> results = new ConcurrentHashMap<>();
    AtomicInteger completedCount = new AtomicInteger(0);

    return executeReadyNodes(graph, parentContext, results, completedCount);
  }

  private Future<String> executeReadyNodes(
      TaskGraph graph,
      SessionContext parentContext,
      Map<String, String> results,
      AtomicInteger completedCount) {
    var readyNodes =
        graph.nodes().stream()
            .filter(
                n -> !results.containsKey(n.id()) && results.keySet().containsAll(n.dependencies()))
            .toList();

    if (readyNodes.isEmpty()) {
      if (completedCount.get() >= graph.nodes().size()) {
        return Future.succeededFuture(formatFinalReport(graph, results));
      }
      return Future.failedFuture("Graph deadlock or cycle detected.");
    }

    var nodeFutures =
        readyNodes.stream().map(node -> executeNode(node, parentContext, results)).toList();

    return Future.all(nodeFutures)
        .compose(
            v -> {
              completedCount.addAndGet(readyNodes.size());
              return executeReadyNodes(graph, parentContext, results, completedCount);
            });
  }

  private Future<Void> executeNode(
      TaskNode node, SessionContext parentContext, Map<String, String> results) {
    logger.info("Starting graph node [{}]: {}", node.id(), node.task());

    String sessionId = parentContext.sessionId() + "-node-" + node.id();
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("graph_node_id", node.id());
    metadata.put("sub_agent_persona", node.persona());

    // Construct node-specific prompt with results from dependencies
    Map<String, String> dependencyResults = new HashMap<>();
    node.dependencies().forEach(depId -> dependencyResults.put(depId, results.get(depId)));

    SessionContext nodeContext = ContextScoper.scope(sessionId, parentContext, metadata);

    AgentLoop childLoop = loopFactory.createLoop();

    StringBuilder promptBuilder = new StringBuilder("TASK: ").append(node.task()).append("\n\n");
    if (!dependencyResults.isEmpty()) {
      promptBuilder.append("PREVIOUS TASK RESULTS:\n");
      dependencyResults.forEach(
          (depId, report) -> {
            promptBuilder.append("ID: ").append(depId).append("\n");
            promptBuilder.append("RESULT:\n").append(report).append("\n");
          });
    }

    return childLoop
        .run(promptBuilder.toString(), nodeContext)
        .map(
            report -> {
              results.put(node.id(), report);
              return (Void) null;
            });
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
