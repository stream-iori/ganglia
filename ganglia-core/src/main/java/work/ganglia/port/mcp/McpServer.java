package work.ganglia.port.mcp;

import io.vertx.core.Future;

public interface McpServer {
    Future<Void> start();
    Future<Void> close();
}
