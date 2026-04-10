package work.ganglia.kernel.loop;

/**
 * Thrown when the agent loop is forcefully aborted via an AgentSignal.
 *
 * @deprecated Use {@link work.ganglia.port.internal.state.AgentAbortedException} instead. This
 *     class is kept for downstream compatibility.
 */
@Deprecated
public class AgentAbortedException extends work.ganglia.port.internal.state.AgentAbortedException {}
