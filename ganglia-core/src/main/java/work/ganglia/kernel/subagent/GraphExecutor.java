package work.ganglia.kernel.subagent;

import io.vertx.core.Future;
import work.ganglia.port.chat.SessionContext;

/**
 * Orchestrates the execution of a TaskGraph.
 */
public interface GraphExecutor {
    /**
     * Executes the given graph and returns a combined report.
     */
    Future<String> execute(TaskGraph graph, SessionContext parentContext);
}
