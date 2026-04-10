package work.ganglia.trading.filter;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;

import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ToolDefinition;
import work.ganglia.port.external.tool.ToolSet;
import work.ganglia.port.external.tool.model.ToolInvokeResult;
import work.ganglia.port.internal.state.ExecutionContext;

/**
 * A {@link ToolSet} decorator that filters tool availability based on persona metadata in {@link
 * SessionContext}. Only sessions whose {@code sub_agent_persona} metadata value is contained in the
 * allowed set will see these tools.
 */
public class RoleAwareToolFilter implements ToolSet {
  private static final Logger logger = LoggerFactory.getLogger(RoleAwareToolFilter.class);

  private final ToolSet delegate;
  private final Set<String> allowedPersonas;

  public RoleAwareToolFilter(ToolSet delegate, Set<String> allowedPersonas) {
    this.delegate = delegate;
    this.allowedPersonas = allowedPersonas;
  }

  @Override
  public boolean isAvailableFor(SessionContext context) {
    if (context == null || context.metadata() == null) {
      return false;
    }
    Object persona = context.metadata().get("sub_agent_persona");
    boolean available = persona instanceof String p && allowedPersonas.contains(p);
    logger.debug("Tool availability for persona={}: {}", persona, available);
    return available;
  }

  @Override
  public List<ToolDefinition> getDefinitions() {
    return delegate.getDefinitions();
  }

  @Override
  public Future<ToolInvokeResult> execute(
      String toolName,
      Map<String, Object> args,
      SessionContext context,
      ExecutionContext executionContext) {
    return delegate.execute(toolName, args, context, executionContext);
  }
}
