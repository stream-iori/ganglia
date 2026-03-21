package work.ganglia;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import java.util.ArrayList;
import java.util.List;
import work.ganglia.config.ConfigManager;
import work.ganglia.infrastructure.external.tool.InteractionTools;
import work.ganglia.infrastructure.external.tool.KnowledgeBaseTools;
import work.ganglia.kernel.AgentEnv;
import work.ganglia.kernel.GangliaKernel;
import work.ganglia.kernel.loop.ReActAgentLoop;
import work.ganglia.kernel.todo.ToDoContextSource;
import work.ganglia.kernel.todo.ToDoTools;
import work.ganglia.port.external.llm.ModelGateway;
import work.ganglia.port.external.tool.ToolExecutor;
import work.ganglia.port.external.tool.ToolSetProvider;
import work.ganglia.port.internal.prompt.ContextSource;
import work.ganglia.port.internal.state.SessionManager;

/** A container for the bootstrapped Ganglia core components. */
public record Ganglia(
    Vertx vertx,
    ModelGateway modelGateway,
    ToolExecutor toolExecutor,
    SessionManager sessionManager,
    ReActAgentLoop agentLoop,
    ConfigManager configManager,
    AgentEnv env,
    int mcpServersCount,
    work.ganglia.infrastructure.mcp.McpRegistry mcpRegistry) {
  /** Shuts down the Ganglia instance and all its components, including MCP servers. */
  public void shutdown() {
    if (mcpRegistry != null) mcpRegistry.close();
    vertx.close();
  }

  /**
   * Initializes the Ganglia framework with default configuration and a new Vertx instance.
   *
   * @return A future that completes with the initialized Ganglia instance.
   */
  public static Future<Ganglia> bootstrap() {
    return bootstrap(Vertx.vertx());
  }

  /**
   * Initializes the Ganglia framework with default configuration.
   *
   * @param vertx The Vertx instance to use.
   * @return A future that completes with the initialized Ganglia instance.
   */
  public static Future<Ganglia> bootstrap(Vertx vertx) {
    return bootstrap(vertx, BootstrapOptions.defaultOptions());
  }

  /**
   * Initializes the Ganglia framework with specific options and a new Vertx instance.
   *
   * @param options Initialization options.
   * @return A future that completes with the initialized Ganglia instance.
   */
  public static Future<Ganglia> bootstrap(BootstrapOptions options) {
    return bootstrap(Vertx.vertx(), options);
  }

  /**
   * Initializes the Ganglia framework with specific options.
   *
   * @param vertx The Vertx instance to use.
   * @param options Initialization options.
   * @return A future that completes with the initialized Ganglia instance.
   */
  public static Future<Ganglia> bootstrap(Vertx vertx, BootstrapOptions options) {
    // Inject Core general-purpose tools and contexts
    List<ToolSetProvider> providers = new ArrayList<>(options.extraToolSetProviders());
    providers.add((v, compressor, memory, root) -> new ToDoTools(v, compressor));
    providers.add((v, compressor, memory, root) -> new KnowledgeBaseTools(v, memory));
    providers.add((v, compressor, memory, root) -> new InteractionTools(v));

    List<ContextSource> contexts = new ArrayList<>(options.extraContextSources());
    contexts.add(new ToDoContextSource());

    BootstrapOptions finalOptions =
        options.withExtraToolSetProviders(providers).withExtraContextSources(contexts);

    return new GangliaKernel(vertx, finalOptions).init();
  }
}
