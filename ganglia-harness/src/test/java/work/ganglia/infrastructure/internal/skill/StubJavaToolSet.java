package work.ganglia.infrastructure.internal.skill;

import java.util.List;
import java.util.Map;

import io.vertx.core.Future;

import work.ganglia.infrastructure.external.tool.model.ToolInvokeResult;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.external.tool.ToolDefinition;
import work.ganglia.port.external.tool.ToolSet;
import work.ganglia.port.internal.state.ExecutionContext;

/** A minimal ToolSet used for testing JavaSkillToolSet dynamic class loading. */
public class StubJavaToolSet implements ToolSet {

  @Override
  public List<ToolDefinition> getDefinitions() {
    return List.of(new ToolDefinition("stub_java_tool", "A stub tool", "{}"));
  }

  @Override
  public Future<ToolInvokeResult> execute(
      ToolCall call, SessionContext context, ExecutionContext executionContext) {
    return Future.succeededFuture(ToolInvokeResult.success("stub output"));
  }

  @Override
  public Future<ToolInvokeResult> execute(
      String toolName,
      Map<String, Object> args,
      SessionContext context,
      ExecutionContext executionContext) {
    return Future.succeededFuture(ToolInvokeResult.success("stub output: " + toolName));
  }
}
