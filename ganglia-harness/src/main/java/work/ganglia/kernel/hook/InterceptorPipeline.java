package work.ganglia.kernel.hook;

import io.vertx.core.Future;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.ganglia.infrastructure.external.tool.model.ToolInvokeResult;
import work.ganglia.port.chat.Message;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ObservationType;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.internal.hook.AgentInterceptor;
import work.ganglia.port.internal.state.ObservationDispatcher;

/**
 * Manages the execution of registered AgentInterceptors. It uses Vert.x Future.compose() to run
 * hooks sequentially. It also handles core lifecycle observations.
 */
public class InterceptorPipeline {
  private static final Logger log = LoggerFactory.getLogger(InterceptorPipeline.class);
  private final List<AgentInterceptor> interceptors = new ArrayList<>();
  private final ObservationDispatcher dispatcher;

  public InterceptorPipeline(ObservationDispatcher dispatcher) {
    this.dispatcher = dispatcher;
  }

  public void addInterceptor(AgentInterceptor interceptor) {
    if (interceptor != null) {
      this.interceptors.add(interceptor);
    }
  }

  public Future<SessionContext> executePreTurn(SessionContext context, String userInput) {
    if (dispatcher != null) {
      dispatcher.dispatch(context.sessionId(), ObservationType.TURN_STARTED, userInput);
    }
    Future<SessionContext> f = Future.succeededFuture(context);
    for (AgentInterceptor hook : interceptors) {
      f = f.compose(ctx -> hook.preTurn(ctx, userInput));
    }
    return f;
  }

  public Future<ToolCall> executePreToolExecute(ToolCall call, SessionContext context) {
    if (dispatcher != null) {
      Map<String, Object> toolData = new HashMap<>();
      toolData.put("toolCallId", call.id());
      if (call.arguments() != null) {
        toolData.putAll(call.arguments());
      }
      dispatcher.dispatch(
          context.sessionId(), ObservationType.TOOL_STARTED, call.toolName(), toolData);
    }
    Future<ToolCall> f = Future.succeededFuture(call);
    for (AgentInterceptor hook : interceptors) {
      f = f.compose(c -> hook.preToolExecute(c, context));
    }
    return f;
  }

  public Future<ToolInvokeResult> executePostToolExecute(
      ToolCall call, ToolInvokeResult result, SessionContext context) {
    if (dispatcher != null) {
      Map<String, Object> resData = Map.of("toolCallId", call.id());
      dispatcher.dispatch(
          context.sessionId(), ObservationType.TOOL_FINISHED, result.output(), resData);
    }
    Future<ToolInvokeResult> f = Future.succeededFuture(result);
    for (AgentInterceptor hook : interceptors) {
      f = f.compose(res -> hook.postToolExecute(call, res, context));
    }
    return f;
  }

  public Future<Void> executePostTurn(SessionContext context, Message finalResponse) {
    if (dispatcher != null) {
      dispatcher.dispatch(
          context.sessionId(), ObservationType.TURN_FINISHED, finalResponse.content());
    }
    Future<Void> f = Future.succeededFuture();
    for (AgentInterceptor hook : interceptors) {
      f =
          f.compose(
              v ->
                  hook.postTurn(context, finalResponse)
                      .recover(
                          err -> {
                            // Fire-and-forget logic: Catch errors here so one failing hook
                            // doesn't stop subsequent postTurn hooks.
                            String hookName = hook.getClass().getSimpleName();
                            if (hookName.isEmpty()) {
                              hookName = hook.getClass().getName();
                            }
                            log.warn(
                                "Interceptor [{}] failed during postTurn, but continuing. Error: {}",
                                hookName,
                                err.getMessage());
                            return Future.succeededFuture();
                          }));
    }
    return f;
  }
}
