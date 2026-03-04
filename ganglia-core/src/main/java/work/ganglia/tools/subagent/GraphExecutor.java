package work.ganglia.tools.subagent;

import io.vertx.core.Future;
import work.ganglia.core.model.SessionContext;

/**
 * Orchestrates the execution of a TaskGraph.
 */
public interface GraphExecutor {
    /**
     * Executes the given graph and returns a combined report.
     */
    Future<String> execute(TaskGraph graph, SessionContext parentContext);
}
