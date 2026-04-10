package work.ganglia.infrastructure.mcp;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import work.ganglia.infrastructure.mcp.transport.StdioMcpTransport;
import work.ganglia.port.external.tool.ToolSet;
import work.ganglia.port.mcp.McpClient;
import work.ganglia.port.mcp.McpInitializeRequest;
import work.ganglia.util.Constants;
import work.ganglia.util.FileSystemUtil;

public class McpConfigManager {
  private static final Logger log = LoggerFactory.getLogger(McpConfigManager.class);
  private static final String DEFAULT_MCP_CONFIG = "{ \"mcpServers\": {} }";
  public static final String FILE_MCP_JSON = ".mcp.json";

  public static Future<McpRegistry> loadMcpToolSets(Vertx vertx, String projectRoot) {
    String mcpConfigPath =
        Paths.get(projectRoot, Constants.DEFAULT_GANGLIA_DIR, FILE_MCP_JSON).toString();

    return FileSystemUtil.ensureFileWithDefault(
            vertx, mcpConfigPath, io.vertx.core.buffer.Buffer.buffer(DEFAULT_MCP_CONFIG))
        .compose(v -> vertx.fileSystem().readFile(mcpConfigPath))
        .compose(
            buffer -> {
              List<Future<McpLoadResult>> futures = new ArrayList<>();
              try {
                JsonObject config = new JsonObject(buffer);
                JsonObject mcpServers = config.getJsonObject("mcpServers", new JsonObject());

                for (String serverName : mcpServers.fieldNames()) {
                  JsonObject serverConfig = mcpServers.getJsonObject(serverName);
                  futures.add(loadServer(vertx, serverName, serverConfig));
                }
              } catch (Exception e) {
                log.error("Failed to parse .mcp.json", e);
              }

              if (futures.isEmpty()) {
                return Future.succeededFuture(new McpRegistry(List.of(), List.of()));
              }

              return Future.all(futures)
                  .map(
                      cf -> {
                        List<ToolSet> toolSets = new ArrayList<>();
                        List<McpClient> clients = new ArrayList<>();
                        for (int i = 0; i < cf.size(); i++) {
                          if (cf.succeeded(i)) {
                            McpLoadResult result = cf.resultAt(i);
                            toolSets.add(result.toolSet());
                            clients.add(result.client());
                          } else {
                            log.error("Failed to load an MCP server", cf.cause(i));
                          }
                        }
                        return new McpRegistry(toolSets, clients);
                      });
            });
  }

  private record McpLoadResult(ToolSet toolSet, McpClient client) {}

  private static Future<McpLoadResult> loadServer(
      Vertx vertx, String serverName, JsonObject config) {
    String command = config.getString("command");
    if (command == null || command.trim().isEmpty()) {
      return Future.failedFuture("MCP server '" + serverName + "' missing command.");
    }

    JsonArray argsArray = config.getJsonArray("args", new JsonArray());
    List<String> commandList = new ArrayList<>();
    commandList.add(command);
    for (int i = 0; i < argsArray.size(); i++) {
      commandList.add(argsArray.getString(i));
    }

    JsonObject envObject = config.getJsonObject("env", new JsonObject());
    Map<String, String> env = new HashMap<>();
    for (String key : envObject.fieldNames()) {
      env.put(key, envObject.getString(key));
    }

    StdioMcpTransport transport = new StdioMcpTransport(vertx, commandList, env);
    McpClient client = new VertxMcpClient(vertx, transport);

    return transport
        .connect()
        .compose(
            v -> {
              McpInitializeRequest.Implementation clientInfo =
                  new McpInitializeRequest.Implementation("Ganglia", "1.0");
              McpInitializeRequest initReq =
                  new McpInitializeRequest("2025-11-25", new HashMap<>(), clientInfo);
              return client.initialize(initReq);
            })
        .compose(res -> McpToolSet.create(client, null, serverName))
        .map(
            ts -> {
              log.info(
                  "Successfully loaded MCP server '{}' with {} tools",
                  serverName,
                  ts.getDefinitions().size());
              return new McpLoadResult((ToolSet) ts, client);
            })
        .recover(
            err -> {
              log.error("Failed to initialize MCP server '{}'", serverName, err);
              return Future.failedFuture(err);
            });
  }
}
