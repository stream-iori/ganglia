package work.ganglia.port.internal.state;

import java.util.Map;

import work.ganglia.port.external.tool.ObservationType;

/**
 * Responsible for receiving macro and micro observations and dispatching them to the underlying
 * event stream (e.g., Vert.x EventBus).
 */
public interface ObservationDispatcher {

  void dispatch(String sessionId, ObservationType type, String content);

  void dispatch(String sessionId, ObservationType type, String content, Map<String, Object> data);
}
