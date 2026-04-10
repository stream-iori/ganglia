package work.ganglia.port.internal.state;

import work.ganglia.port.chat.SessionContext;

/** Evaluates context pressure and emits warnings. */
public interface PressureEvaluator {
  ContextPressure evaluateAndNotify(SessionContext context);
}
