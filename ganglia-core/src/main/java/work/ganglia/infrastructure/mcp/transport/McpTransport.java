package work.ganglia.infrastructure.mcp.transport;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.core.streams.ReadStream;

/**
 * Defines a transport layer for MCP (Model Context Protocol).
 */
public interface McpTransport {
    /**
     * Connects and starts the transport.
     * @return a future that completes when the connection is established
     */
    Future<Void> connect();

    /**
     * Sends a JSON-RPC message over the transport.
     * @param message the message to send
     * @return a future that completes when the message is sent
     */
    Future<Void> send(JsonObject message);

    /**
     * Returns a stream of incoming JSON-RPC messages.
     * @return the read stream
     */
    ReadStream<JsonObject> messageStream();

    /**
     * Closes the transport.
     * @return a future that completes when closed
     */
    Future<Void> close();
}
