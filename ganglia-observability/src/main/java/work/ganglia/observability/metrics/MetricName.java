package work.ganglia.observability.metrics;

/** Names for all tracked observability metrics. */
public enum MetricName {
  // Model call layer
  MODEL_CALL_LATENCY_MS,
  MODEL_CALL_TTFT_MS,
  TOKEN_PROMPT_TOTAL,
  TOKEN_COMPLETION_TOTAL,
  PROMPT_CACHE_HIT_RATE,

  // Tool execution layer
  TOOL_EXECUTION_DURATION_MS,
  TOOL_SUCCESS_COUNT,
  TOOL_FAILURE_COUNT,
  MCP_CALL_DURATION_MS,

  // Session / Turn layer
  TURN_DURATION_MS,
  SESSION_DURATION_MS,
  TOOLS_PER_TURN,
  CONTEXT_COMPRESSION_COUNT,

  // Error layer
  ERROR_COUNT,
  SESSION_ABORT_COUNT
}
