package work.ganglia.kernel.task;

import io.vertx.core.Future;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.ganglia.kernel.loop.AgentLoop;
import work.ganglia.kernel.loop.AgentLoopFactory;
import work.ganglia.kernel.subagent.ContextScoper;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.internal.state.ExecutionContext;

/** SRP: Task that executes a sub-agent turn using a child ReAct loop. */
public class SubAgentTask implements AgentTask {
  private static final Logger logger = LoggerFactory.getLogger(SubAgentTask.class);

  private final ToolCall call;
  private final AgentLoopFactory loopFactory;

  public SubAgentTask(ToolCall call, AgentLoopFactory loopFactory) {
    this.call = call;
    this.loopFactory = loopFactory;
  }

  @Override
  public String id() {
    return call.id();
  }

  @Override
  public String name() {
    return call.toolName();
  }

  @Override
  public ToolCall getToolCall() {
    return call;
  }

  @Override
  public Future<AgentTaskResult> execute(
      SessionContext parentContext, ExecutionContext executionContext) {
    String task = (String) call.arguments().get("task");
    String persona = (String) call.arguments().getOrDefault("persona", "GENERAL");

    logger.info("Executing Sub-Agent task (Persona: {}): {}", persona, task);

    String childSessionId =
        parentContext.sessionId() + "-sub-" + UUID.randomUUID().toString().substring(0, 4);
    Map<String, Object> childMetadata = new HashMap<>();
    childMetadata.put("is_sub_agent", true);
    childMetadata.put("sub_agent_persona", persona);

    // Recursion depth limit
    Object levelObj = parentContext.metadata().get("sub_agent_level");
    int currentLevel = (levelObj instanceof Integer) ? (Integer) levelObj : 0;
    if (currentLevel >= 3) {
      logger.warn("Sub-agent recursion depth limit reached (level: {})", currentLevel);
      return Future.succeededFuture(
          AgentTaskResult.error("RECURSION_LIMIT: Sub-agent depth limit reached."));
    }
    childMetadata.put("sub_agent_level", currentLevel + 1);

    SessionContext childContext = ContextScoper.scope(childSessionId, parentContext, childMetadata);

    AgentLoop childLoop = loopFactory.createLoop();

    return childLoop
        .run("TASK: " + task, childContext)
        .map(
            report ->
                AgentTaskResult.success(
                    "--- SUB-AGENT REPORT ---\n" + report + "\n--- END REPORT ---"))
        .recover(
            err ->
                Future.succeededFuture(
                    AgentTaskResult.error("SUB_AGENT_ERROR: " + err.getMessage())));
  }
}
