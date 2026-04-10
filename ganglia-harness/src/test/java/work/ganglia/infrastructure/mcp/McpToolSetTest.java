package work.ganglia.infrastructure.mcp;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.Future;

import work.ganglia.port.external.tool.ObservationType;
import work.ganglia.port.external.tool.model.ToolInvokeResult;
import work.ganglia.port.internal.state.ExecutionContext;
import work.ganglia.port.internal.state.ObservationDispatcher;
import work.ganglia.port.mcp.McpCallToolRequest;
import work.ganglia.port.mcp.McpCallToolResult;
import work.ganglia.port.mcp.McpClient;
import work.ganglia.port.mcp.McpListToolsResult;
import work.ganglia.port.mcp.McpTool;

class McpToolSetTest {

  private McpClient mockClient;
  private ExecutionContext mockExecCtx;

  /** Captures dispatched observations for assertions. */
  private final List<CapturedDispatch> dispatches = new ArrayList<>();

  private record CapturedDispatch(
      String sessionId,
      ObservationType type,
      String content,
      Map<String, Object> data,
      String spanId,
      String parentSpanId) {}

  private final ObservationDispatcher capturingDispatcher =
      new ObservationDispatcher() {
        @Override
        public void dispatch(String sessionId, ObservationType type, String content) {
          dispatches.add(new CapturedDispatch(sessionId, type, content, null, null, null));
        }

        @Override
        public void dispatch(
            String sessionId, ObservationType type, String content, Map<String, Object> data) {
          dispatches.add(new CapturedDispatch(sessionId, type, content, data, null, null));
        }

        @Override
        public void dispatch(
            String sessionId,
            ObservationType type,
            String content,
            Map<String, Object> data,
            String spanId,
            String parentSpanId) {
          dispatches.add(
              new CapturedDispatch(sessionId, type, content, data, spanId, parentSpanId));
        }
      };

  @BeforeEach
  void setUp() {
    dispatches.clear();

    mockClient = mock(McpClient.class);
    when(mockClient.listTools())
        .thenReturn(
            Future.succeededFuture(
                new McpListToolsResult(
                    List.of(
                        new McpTool(
                            "echo",
                            "Echoes input",
                            Map.of("type", "object", "properties", Map.of()))))));

    when(mockClient.callTool(any(McpCallToolRequest.class)))
        .thenReturn(
            Future.succeededFuture(
                new McpCallToolResult(
                    List.of(new McpCallToolResult.Content("text", "hello", null, null)), false)));

    mockExecCtx = mock(ExecutionContext.class);
    when(mockExecCtx.sessionId()).thenReturn("sess-1");
    when(mockExecCtx.spanId()).thenReturn("parent-span");
  }

  @Test
  void executeWithoutDispatcher_noEventsStillReturnsResult() {
    McpToolSet toolSet = McpToolSet.create(mockClient, null, "test-server").result();

    ToolInvokeResult result =
        toolSet.execute("echo", Map.of("msg", "hi"), null, mockExecCtx).result();

    assertEquals(ToolInvokeResult.Status.SUCCESS, result.status());
    assertEquals("hello", result.output());
    assertTrue(dispatches.isEmpty(), "No events should be dispatched without a dispatcher");
  }

  @Test
  void setDispatcher_thenExecute_dispatchesStartedAndFinished() {
    McpToolSet toolSet = McpToolSet.create(mockClient, null, "my-mcp-server").result();

    // Late-bind dispatcher (mimics GangliaKernel wiring)
    toolSet.setDispatcher(capturingDispatcher);

    ToolInvokeResult result =
        toolSet.execute("echo", Map.of("msg", "hi"), null, mockExecCtx).result();

    assertEquals(ToolInvokeResult.Status.SUCCESS, result.status());
    assertEquals(2, dispatches.size(), "Should have STARTED + FINISHED events");

    CapturedDispatch started = dispatches.get(0);
    assertEquals("sess-1", started.sessionId());
    assertEquals(ObservationType.MCP_CALL_STARTED, started.type());
    assertEquals("echo", started.content());
    assertEquals("echo", started.data().get("toolName"));
    assertEquals("my-mcp-server", started.data().get("serverName"));
    assertNotNull(started.spanId(), "spanId should be set");
    assertEquals("parent-span", started.parentSpanId());

    CapturedDispatch finished = dispatches.get(1);
    assertEquals("sess-1", finished.sessionId());
    assertEquals(ObservationType.MCP_CALL_FINISHED, finished.type());
    assertEquals("echo", finished.content());
    assertEquals("echo", finished.data().get("toolName"));
    assertEquals("my-mcp-server", finished.data().get("serverName"));
    assertEquals("SUCCESS", finished.data().get("status"));
    assertNotNull(finished.data().get("durationMs"), "durationMs should be present");
    assertEquals(started.spanId(), finished.spanId(), "Same spanId for start and finish");
    assertEquals("parent-span", finished.parentSpanId());
  }

  @Test
  void executeError_dispatchesFinishedWithErrorStatus() {
    when(mockClient.callTool(any(McpCallToolRequest.class)))
        .thenReturn(
            Future.succeededFuture(
                new McpCallToolResult(
                    List.of(new McpCallToolResult.Content("text", "bad input", null, null)),
                    true)));

    McpToolSet toolSet = McpToolSet.create(mockClient, null, "err-server").result();
    toolSet.setDispatcher(capturingDispatcher);

    ToolInvokeResult result = toolSet.execute("failTool", Map.of(), null, mockExecCtx).result();

    assertEquals(ToolInvokeResult.Status.ERROR, result.status());
    assertEquals(2, dispatches.size());

    CapturedDispatch finished = dispatches.get(1);
    assertEquals(ObservationType.MCP_CALL_FINISHED, finished.type());
    assertEquals("ERROR", finished.data().get("status"));
  }
}
