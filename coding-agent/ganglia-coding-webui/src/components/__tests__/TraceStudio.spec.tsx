import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom';
import TraceStudio from '../TraceStudio';

const mockFetch = vi.fn();
window.fetch = mockFetch;

class MockWebSocket {
  static instances: MockWebSocket[] = [];
  onopen: (() => void) | null = null;
  onmessage: ((e: { data: string }) => void) | null = null;
  onclose: (() => void) | null = null;
  onerror: (() => void) | null = null;
  readyState = 0;
  close = vi.fn(() => {
    this.readyState = 3;
    this.onclose?.();
  });
  constructor(public url: string) {
    MockWebSocket.instances.push(this);
  }
  simulateOpen() {
    this.readyState = 1;
    this.onopen?.();
  }
  simulateMessage(payload: unknown) {
    this.onmessage?.({ data: JSON.stringify(payload) });
  }
}

const OriginalWebSocket = window.WebSocket;

function ev(overrides: Record<string, unknown> = {}) {
  return {
    sessionId: 'sess-001',
    type: 'TOOL_STARTED',
    content: 'test',
    data: {},
    timestamp: Date.now(),
    ...overrides,
  };
}

function setupFileList(files: string[] = ['trace-2026-03-28.jsonl']) {
  mockFetch.mockResolvedValueOnce({ json: async () => files });
}

async function selectFile(filename: string, events: unknown[]) {
  mockFetch.mockResolvedValueOnce({ json: async () => events });
  fireEvent.click(screen.getByText(filename.replace('.jsonl', '')));
  await waitFor(() => {});
}

describe('TraceStudio Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    MockWebSocket.instances = [];
    window.WebSocket = MockWebSocket as unknown as typeof WebSocket;
  });

  afterEach(() => {
    window.WebSocket = OriginalWebSocket;
  });

  it('renders trace files and allows selection with tree collapse/expand', async () => {
    setupFileList(['trace-2026-03-28.jsonl', 'trace-2026-03-27.jsonl']);
    render(<TraceStudio />);

    await waitFor(() => {
      expect(screen.getByText('trace-2026-03-28')).toBeInTheDocument();
      expect(screen.getByText('trace-2026-03-27')).toBeInTheDocument();
    });

    await selectFile('trace-2026-03-28.jsonl', [
      ev({ type: 'SESSION_STARTED', timestamp: 1000, spanId: 's1', content: 'Initial prompt', data: { firstPrompt: 'hello' } }),
      ev({ type: 'TOOL_STARTED', timestamp: 2000, spanId: 't1', parentSpanId: 's1', content: 'read_file', data: { path: 'test.txt' } }),
      ev({ type: 'TOOL_FINISHED', timestamp: 3000, spanId: 't1', parentSpanId: 's1', content: 'SUCCESS', data: { durationMs: 150, status: 'success' } }),
    ]);

    await waitFor(() => {
      expect(screen.getByText('SESSION')).toBeInTheDocument();
      expect(screen.getByText('Initial prompt')).toBeInTheDocument();
      expect(screen.getByText('TOOL')).toBeInTheDocument();
      expect(screen.getByText('read_file')).toBeInTheDocument();
    });

    expect(screen.getByText('150ms')).toBeInTheDocument();

    fireEvent.click(screen.getByText('SESSION').closest('div.group')!);
    await waitFor(() => expect(screen.queryByText('read_file')).not.toBeInTheDocument());

    fireEvent.click(screen.getByText('SESSION').closest('div.group')!);
    await waitFor(() => expect(screen.getByText('read_file')).toBeInTheDocument());
  });

  it('groups events by sessionId', async () => {
    setupFileList();
    render(<TraceStudio />);
    await waitFor(() => expect(screen.getByText('trace-2026-03-28')).toBeInTheDocument());

    await selectFile('trace-2026-03-28.jsonl', [
      ev({ sessionId: 'sess-A', type: 'SESSION_STARTED', timestamp: 1000, spanId: 'sa', content: 'Session A' }),
      ev({ sessionId: 'sess-B', type: 'SESSION_STARTED', timestamp: 2000, spanId: 'sb', content: 'Session B' }),
    ]);

    await waitFor(() => {
      expect(screen.getByText('sess-A')).toBeInTheDocument();
      expect(screen.getByText('sess-B')).toBeInTheDocument();
      expect(screen.getAllByText('Session').length).toBeGreaterThanOrEqual(2);
    });
  });

  it('shows empty state when no file is selected', () => {
    mockFetch.mockResolvedValueOnce({ json: async () => [] });
    render(<TraceStudio />);
    expect(screen.getByText(/Select an execution trace/i)).toBeInTheDocument();
  });

  it('renders deeply nested spans correctly', async () => {
    setupFileList();
    render(<TraceStudio />);
    await waitFor(() => expect(screen.getByText('trace-2026-03-28')).toBeInTheDocument());

    await selectFile('trace-2026-03-28.jsonl', [
      ev({ type: 'SESSION_STARTED', timestamp: 1000, spanId: 'root', content: 'Root' }),
      ev({ type: 'TOOL_STARTED', timestamp: 2000, spanId: 'child1', parentSpanId: 'root', content: 'Child 1' }),
      ev({ type: 'TOOL_STARTED', timestamp: 3000, spanId: 'grandchild', parentSpanId: 'child1', content: 'Grandchild' }),
    ]);

    await waitFor(() => {
      expect(screen.getByText('Root')).toBeInTheDocument();
      expect(screen.getByText('Child 1')).toBeInTheDocument();
      expect(screen.getByText('Grandchild')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText('Child 1').closest('div.group')!);
    await waitFor(() => expect(screen.queryByText('Grandchild')).not.toBeInTheDocument());

    fireEvent.click(screen.getByText('Root').closest('div.group')!);
    await waitFor(() => expect(screen.queryByText('Child 1')).not.toBeInTheDocument());
  });

  it('renders events without spanId as flat events', async () => {
    setupFileList();
    render(<TraceStudio />);
    await waitFor(() => expect(screen.getByText('trace-2026-03-28')).toBeInTheDocument());

    await selectFile('trace-2026-03-28.jsonl', [
      ev({ type: 'SESSION_STARTED', timestamp: 1000, spanId: 'root', content: 'Root' }),
      { sessionId: 'sess-001', type: 'SYSTEM_LOG', content: 'Flat event', data: {}, timestamp: 1500 },
    ]);

    await waitFor(() => {
      expect(screen.getByText('Root')).toBeInTheDocument();
      expect(screen.getByText('Flat event')).toBeInTheDocument();
      expect(screen.getByText('SYSTEM_LOG')).toBeInTheDocument();
    });
  });

  it('strips _STARTED, _FINISHED, _RECORDED suffixes from type labels', async () => {
    setupFileList();
    render(<TraceStudio />);
    await waitFor(() => expect(screen.getByText('trace-2026-03-28')).toBeInTheDocument());

    await selectFile('trace-2026-03-28.jsonl', [
      ev({ type: 'MODEL_CALL_STARTED', timestamp: 1000, spanId: 'm1', content: 'gpt-4' }),
      ev({ type: 'MODEL_CALL_FINISHED', timestamp: 2000, spanId: 'm1', content: 'done', data: { durationMs: 500 } }),
      ev({ type: 'TOKEN_USAGE_RECORDED', timestamp: 3000, spanId: 'tu1', content: 'usage', data: { promptTokens: 100, completionTokens: 50, totalTokens: 150 } }),
    ]);

    await waitFor(() => {
      expect(screen.getByText('MODEL_CALL')).toBeInTheDocument();
      expect(screen.getByText('TOKEN_USAGE')).toBeInTheDocument();
      expect(screen.queryByText('MODEL_CALL_STARTED')).not.toBeInTheDocument();
      expect(screen.queryByText('TOKEN_USAGE_RECORDED')).not.toBeInTheDocument();
    });
  });

  it('merges startEvent and endEvent data for display', async () => {
    setupFileList();
    render(<TraceStudio />);
    await waitFor(() => expect(screen.getByText('trace-2026-03-28')).toBeInTheDocument());

    await selectFile('trace-2026-03-28.jsonl', [
      ev({ type: 'TOOL_STARTED', timestamp: 1000, spanId: 'tool1', content: 'run_shell', data: { command: 'ls -la' } }),
      ev({ type: 'TOOL_FINISHED', timestamp: 2000, spanId: 'tool1', content: 'done', data: { durationMs: 300, status: 'success', exitCode: 0 } }),
    ]);

    await waitFor(() => {
      expect(screen.getByText('run_shell')).toBeInTheDocument();
      expect(screen.getByText('300ms')).toBeInTheDocument();
    });
  });

  it('renders CONTEXT_COMPRESSED with before/after tokens and compression stats', async () => {
    setupFileList();
    render(<TraceStudio />);
    await waitFor(() => expect(screen.getByText('trace-2026-03-28')).toBeInTheDocument());

    await selectFile('trace-2026-03-28.jsonl', [
      ev({
        type: 'CONTEXT_COMPRESSED', timestamp: 1000, spanId: 'cc1',
        content: 'compression',
        data: { beforeTokens: 50000, afterTokens: 12000, contextLimit: 128000, compressionTarget: 62000, historyBudget: 80000 },
      }),
    ]);

    await waitFor(() => {
      expect(screen.getByText('Context Compression')).toBeInTheDocument();
      // Numbers are now formatted with toLocaleString()
      expect(screen.getByText('50,000')).toBeInTheDocument();
      expect(screen.getByText('12,000')).toBeInTheDocument();
      expect(screen.getByText('128,000')).toBeInTheDocument();
    });

    // Compression ratio: (50000-12000)/50000 = 76.0%
    expect(screen.getByText(/38,000 \(76\.0%\)/)).toBeInTheDocument();
    // compressionTarget shown
    expect(screen.getByText('62,000')).toBeInTheDocument();
    // On-target: 12000 <= 62000 → ✓
    expect(screen.getByText('✓')).toBeInTheDocument();
    // Bar chart legend
    expect(screen.getByText('Before')).toBeInTheDocument();
    expect(screen.getByText('After')).toBeInTheDocument();
  });

  it('renders CONTEXT_COMPRESSED with amber warning when not on target', async () => {
    setupFileList();
    render(<TraceStudio />);
    await waitFor(() => expect(screen.getByText('trace-2026-03-28')).toBeInTheDocument());

    await selectFile('trace-2026-03-28.jsonl', [
      ev({
        type: 'CONTEXT_COMPRESSED', timestamp: 1000, spanId: 'cc2',
        content: 'compression',
        data: { beforeTokens: 50000, afterTokens: 45000, compressionTarget: 30000 },
      }),
    ]);

    await waitFor(() => {
      // after (45000) > compressionTarget (30000) → ⚠
      expect(screen.getByText('⚠')).toBeInTheDocument();
      // compression ratio = 10% → amber color
      const savedEl = screen.getByText(/5,000 \(10\.0%\)/);
      expect(savedEl.className).toContain('amber');
    });
  });

  it('renders TOKEN_USAGE_RECORDED with prompt/completion/total', async () => {
    setupFileList();
    render(<TraceStudio />);
    await waitFor(() => expect(screen.getByText('trace-2026-03-28')).toBeInTheDocument());

    await selectFile('trace-2026-03-28.jsonl', [
      ev({
        type: 'TOKEN_USAGE_RECORDED', timestamp: 1000, spanId: 'tu1',
        content: 'usage',
        data: { promptTokens: 2500, completionTokens: 800, totalTokens: 3300 },
      }),
    ]);

    await waitFor(() => {
      expect(screen.getByText('Prompt')).toBeInTheDocument();
      expect(screen.getByText('2500')).toBeInTheDocument();
      expect(screen.getByText('Completion')).toBeInTheDocument();
      expect(screen.getByText('800')).toBeInTheDocument();
      expect(screen.getByText('Total')).toBeInTheDocument();
      expect(screen.getByText('3300')).toBeInTheDocument();
    });
  });

  it('filters internal fields from generic data display', async () => {
    setupFileList();
    render(<TraceStudio />);
    await waitFor(() => expect(screen.getByText('trace-2026-03-28')).toBeInTheDocument());

    await selectFile('trace-2026-03-28.jsonl', [
      ev({
        type: 'TOOL_STARTED', timestamp: 1000, spanId: 'f1', content: 'test_tool',
        data: {
          durationMs: 200, attempt: 1, model: 'gpt-4',
          status: 'success', toolCallId: 'call_abc',
          customField: 'visible_value', path: '/test/path',
        },
      }),
    ]);

    await waitFor(() => {
      expect(screen.getByText('test_tool')).toBeInTheDocument();
      expect(screen.getByText('visible_value')).toBeInTheDocument();
      expect(screen.getByText('/test/path')).toBeInTheDocument();
    });
  });

  it('shows "Thinking..." for REASONING_STARTED with empty content', async () => {
    setupFileList();
    render(<TraceStudio />);
    await waitFor(() => expect(screen.getByText('trace-2026-03-28')).toBeInTheDocument());

    await selectFile('trace-2026-03-28.jsonl', [
      ev({ type: 'REASONING_STARTED', timestamp: 1000, spanId: 'r1', content: '' }),
    ]);

    await waitFor(() => expect(screen.getByText('Thinking...')).toBeInTheDocument());
  });

  it('shows success icon when status is success', async () => {
    setupFileList();
    render(<TraceStudio />);
    await waitFor(() => expect(screen.getByText('trace-2026-03-28')).toBeInTheDocument());

    await selectFile('trace-2026-03-28.jsonl', [
      ev({ type: 'TOOL_STARTED', timestamp: 1000, spanId: 'ts1', content: 'my_tool' }),
      ev({ type: 'TOOL_FINISHED', timestamp: 2000, spanId: 'ts1', content: 'done', data: { status: 'success', durationMs: 100 } }),
    ]);

    await waitFor(() => expect(screen.getByText('my_tool')).toBeInTheDocument());
    const container = screen.getByText('my_tool').closest('div.flex-1');
    expect(container?.querySelector('.text-emerald-500')).toBeInTheDocument();
  });

  it('shows error icon when status is failed', async () => {
    setupFileList();
    render(<TraceStudio />);
    await waitFor(() => expect(screen.getByText('trace-2026-03-28')).toBeInTheDocument());

    await selectFile('trace-2026-03-28.jsonl', [
      ev({ type: 'TOOL_STARTED', timestamp: 1000, spanId: 'tf1', content: 'failing_tool' }),
      ev({ type: 'TOOL_FINISHED', timestamp: 2000, spanId: 'tf1', content: 'error', data: { status: 'failed', durationMs: 50 } }),
    ]);

    await waitFor(() => expect(screen.getByText('failing_tool')).toBeInTheDocument());
    const container = screen.getByText('failing_tool').closest('div.flex-1');
    expect(container?.querySelector('.text-red-500')).toBeInTheDocument();
  });

  it('shows retry badge when attempt > 1', async () => {
    setupFileList();
    render(<TraceStudio />);
    await waitFor(() => expect(screen.getByText('trace-2026-03-28')).toBeInTheDocument());

    await selectFile('trace-2026-03-28.jsonl', [
      ev({ type: 'MODEL_CALL_STARTED', timestamp: 1000, spanId: 'retry1', content: 'call', data: { attempt: 3 } }),
    ]);

    await waitFor(() => expect(screen.getByText('Retry #3')).toBeInTheDocument());
  });

  it('does not show retry badge when attempt is 1', async () => {
    setupFileList();
    render(<TraceStudio />);
    await waitFor(() => expect(screen.getByText('trace-2026-03-28')).toBeInTheDocument());

    await selectFile('trace-2026-03-28.jsonl', [
      ev({ type: 'MODEL_CALL_STARTED', timestamp: 1000, spanId: 'no-retry', content: 'call', data: { attempt: 1 } }),
    ]);

    await waitFor(() => expect(screen.getByText('call')).toBeInTheDocument());
    expect(screen.queryByText(/Retry/)).not.toBeInTheDocument();
  });

  it('shows model badge when model is present', async () => {
    setupFileList();
    render(<TraceStudio />);
    await waitFor(() => expect(screen.getByText('trace-2026-03-28')).toBeInTheDocument());

    await selectFile('trace-2026-03-28.jsonl', [
      ev({ type: 'MODEL_CALL_STARTED', timestamp: 1000, spanId: 'mb1', content: 'chat', data: { model: 'claude-3-opus' } }),
    ]);

    await waitFor(() => expect(screen.getByText('claude-3-opus')).toBeInTheDocument());
  });

  it('shows total and session event counts', async () => {
    setupFileList();
    render(<TraceStudio />);
    await waitFor(() => expect(screen.getByText('trace-2026-03-28')).toBeInTheDocument());

    await selectFile('trace-2026-03-28.jsonl', [
      ev({ type: 'SESSION_STARTED', timestamp: 1000, spanId: 'e1', content: 'start' }),
      ev({ type: 'TOOL_STARTED', timestamp: 2000, spanId: 'e2', parentSpanId: 'e1', content: 'tool' }),
      ev({ type: 'TOOL_FINISHED', timestamp: 3000, spanId: 'e2', parentSpanId: 'e1', content: 'done', data: { durationMs: 100 } }),
    ]);

    await waitFor(() => {
      expect(screen.getByText('3 EVENTS')).toBeInTheDocument();
      expect(screen.getByText('3 events')).toBeInTheDocument();
    });
  });

  it('shows "N sessions" when multiple sessions', async () => {
    setupFileList();
    render(<TraceStudio />);
    await waitFor(() => expect(screen.getByText('trace-2026-03-28')).toBeInTheDocument());

    await selectFile('trace-2026-03-28.jsonl', [
      ev({ sessionId: 'sess-A', type: 'SESSION_STARTED', timestamp: 1000, spanId: 'a1', content: 'A' }),
      ev({ sessionId: 'sess-B', type: 'SESSION_STARTED', timestamp: 2000, spanId: 'b1', content: 'B' }),
      ev({ sessionId: 'sess-C', type: 'SESSION_STARTED', timestamp: 3000, spanId: 'c1', content: 'C' }),
    ]);

    await waitFor(() => expect(screen.getByText('3 sessions')).toBeInTheDocument());
  });

  it('shows empty file message when no events', async () => {
    setupFileList();
    render(<TraceStudio />);
    await waitFor(() => expect(screen.getByText('trace-2026-03-28')).toBeInTheDocument());

    await selectFile('trace-2026-03-28.jsonl', []);
    await waitFor(() => expect(screen.getByText('No events in this trace file')).toBeInTheDocument());
  });

  it('connects to WebSocket in live mode', async () => {
    setupFileList();
    render(<TraceStudio />);
    await waitFor(() => expect(screen.getByText('Trace Studio')).toBeInTheDocument());

    fireEvent.click(screen.getByText('Connect Live'));

    await waitFor(() => {
      expect(screen.getByText('Live Trace')).toBeInTheDocument();
      expect(screen.getByText('Waiting for events...')).toBeInTheDocument();
    });

    const ws = MockWebSocket.instances[MockWebSocket.instances.length - 1];
    ws.simulateOpen();
    await waitFor(() => expect(screen.getByText('Live')).toBeInTheDocument());

    ws.simulateMessage({
      sessionId: 'live-sess', type: 'SESSION_STARTED', content: 'Live session',
      data: {}, timestamp: Date.now(), spanId: 'ls1',
    });

    await waitFor(() => {
      expect(screen.getByText('live-sess')).toBeInTheDocument();
      expect(screen.getByText('Live session')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText('Live'));
    await waitFor(() => expect(ws.close).toHaveBeenCalled());
  });

  it('sorts children by timestamp', async () => {
    setupFileList();
    render(<TraceStudio />);
    await waitFor(() => expect(screen.getByText('trace-2026-03-28')).toBeInTheDocument());

    await selectFile('trace-2026-03-28.jsonl', [
      ev({ type: 'SESSION_STARTED', timestamp: 1000, spanId: 'root', content: 'Root' }),
      ev({ type: 'TOOL_STARTED', timestamp: 5000, spanId: 'late', parentSpanId: 'root', content: 'Late Child' }),
      ev({ type: 'TOOL_STARTED', timestamp: 2000, spanId: 'early', parentSpanId: 'root', content: 'Early Child' }),
    ]);

    await waitFor(() => {
      expect(screen.getByText('Early Child')).toBeInTheDocument();
      expect(screen.getByText('Late Child')).toBeInTheDocument();
    });

    const text = document.body.textContent || '';
    expect(text.indexOf('Early Child')).toBeLessThan(text.indexOf('Late Child'));
  });

  it('renders orphan span with missing parent as root', async () => {
    setupFileList();
    render(<TraceStudio />);
    await waitFor(() => expect(screen.getByText('trace-2026-03-28')).toBeInTheDocument());

    await selectFile('trace-2026-03-28.jsonl', [
      ev({ type: 'TOOL_STARTED', timestamp: 1000, spanId: 'orphan1', parentSpanId: 'nonexistent', content: 'Orphan tool' }),
    ]);

    await waitFor(() => {
      expect(screen.getByText('Orphan tool')).toBeInTheDocument();
      expect(screen.getByText('TOOL')).toBeInTheDocument();
    });
  });

  it('shows "No traces found" when file list is empty', async () => {
    setupFileList([]);
    render(<TraceStudio />);
    await waitFor(() => expect(screen.getByText(/No traces found/i)).toBeInTheDocument());
  });

  it('truncates long content and shows "Show full" button', async () => {
    const longContent = 'A'.repeat(500);
    setupFileList();
    render(<TraceStudio />);
    await waitFor(() => expect(screen.getByText('trace-2026-03-28')).toBeInTheDocument());

    await selectFile('trace-2026-03-28.jsonl', [
      ev({ type: 'TOOL_STARTED', timestamp: 1000, spanId: 'lc1', content: longContent }),
    ]);

    await waitFor(() => {
      // Should show truncated text (300 chars + …)
      expect(screen.queryByText(longContent)).not.toBeInTheDocument();
      expect(screen.getByText('Show full')).toBeInTheDocument();
    });
  });

  it('does not truncate short content', async () => {
    const shortContent = 'Short content here';
    setupFileList();
    render(<TraceStudio />);
    await waitFor(() => expect(screen.getByText('trace-2026-03-28')).toBeInTheDocument());

    await selectFile('trace-2026-03-28.jsonl', [
      ev({ type: 'TOOL_STARTED', timestamp: 1000, spanId: 'sc1', content: shortContent }),
    ]);

    await waitFor(() => {
      expect(screen.getByText(shortContent)).toBeInTheDocument();
      expect(screen.queryByText('Show full')).not.toBeInTheDocument();
    });
  });

  it('opens modal when "Show full" is clicked on truncated content', async () => {
    const longContent = 'B'.repeat(500);
    setupFileList();
    render(<TraceStudio />);
    await waitFor(() => expect(screen.getByText('trace-2026-03-28')).toBeInTheDocument());

    await selectFile('trace-2026-03-28.jsonl', [
      ev({ type: 'TOOL_STARTED', timestamp: 1000, spanId: 'modal1', content: longContent }),
    ]);

    await waitFor(() => expect(screen.getByText('Show full')).toBeInTheDocument());
    fireEvent.click(screen.getByText('Show full'));

    await waitFor(() => {
      expect(screen.getByText('Full Content')).toBeInTheDocument();
      // Modal should show the full content
      expect(screen.getByText(longContent)).toBeInTheDocument();
    });
  });

  it('closes modal when close button is clicked', async () => {
    const longContent = 'C'.repeat(500);
    setupFileList();
    render(<TraceStudio />);
    await waitFor(() => expect(screen.getByText('trace-2026-03-28')).toBeInTheDocument());

    await selectFile('trace-2026-03-28.jsonl', [
      ev({ type: 'TOOL_STARTED', timestamp: 1000, spanId: 'close1', content: longContent }),
    ]);

    await waitFor(() => expect(screen.getByText('Show full')).toBeInTheDocument());
    fireEvent.click(screen.getByText('Show full'));
    await waitFor(() => expect(screen.getByText('Full Content')).toBeInTheDocument());

    // Click the close button (X icon's parent button with title="Close")
    fireEvent.click(screen.getByTitle('Close'));
    await waitFor(() => expect(screen.queryByText('Full Content')).not.toBeInTheDocument());
  });

  it('truncates long data field values and opens modal', async () => {
    const longValue = 'D'.repeat(600);
    setupFileList();
    render(<TraceStudio />);
    await waitFor(() => expect(screen.getByText('trace-2026-03-28')).toBeInTheDocument());

    await selectFile('trace-2026-03-28.jsonl', [
      ev({
        type: 'TOOL_STARTED', timestamp: 1000, spanId: 'dv1', content: 'test_tool',
        data: { output: longValue },
      }),
    ]);

    await waitFor(() => {
      expect(screen.getByText('test_tool')).toBeInTheDocument();
      // Full value should not be rendered
      expect(screen.queryByText(longValue)).not.toBeInTheDocument();
      // "Show full" button should be visible
      expect(screen.getByText('Show full')).toBeInTheDocument();
    });

    // Click to open modal
    fireEvent.click(screen.getByText('Show full'));
    await waitFor(() => {
      expect(screen.getByText('Full Content')).toBeInTheDocument();
      expect(screen.getByText(longValue)).toBeInTheDocument();
    });
  });

  it('assigns correct color classes for all event types', async () => {
    setupFileList();
    render(<TraceStudio />);
    await waitFor(() => expect(screen.getByText('trace-2026-03-28')).toBeInTheDocument());

    const types = [
      { type: 'ERROR_OCCURRED', color: 'text-red' },
      { type: 'SKILL_STARTED', color: 'text-purple' },
      { type: 'MCP_CALL_STARTED', color: 'text-orange' },
      { type: 'TOOL_STARTED', color: 'text-amber' },
      { type: 'MODEL_CALL_STARTED', color: 'text-blue' },
      { type: 'CONTEXT_COMPRESSED', color: 'text-emerald' },
      { type: 'SESSION_STARTED', color: 'text-indigo' },
      { type: 'TURN_STARTED', color: 'text-cyan' },
      { type: 'UNKNOWN_TYPE', color: 'text-slate' },
    ];

    await selectFile('trace-2026-03-28.jsonl',
      types.map((t, i) => ev({
        type: t.type, timestamp: 1000 + i * 100,
        spanId: 'color-' + i, content: 'C-' + t.type,
      })),
    );

    // Wait for at least some events to render
    await waitFor(() => {
      expect(screen.getByText('C-ERROR_OCCURRED')).toBeInTheDocument();
    });

    // Verify type label badges have correct color
    types.forEach((t) => {
      const label = t.type
        .replace('_STARTED', '')
        .replace('_FINISHED', '')
        .replace('_RECORDED', '');
      const badges = screen.getAllByText(label);
      const badge = badges.find((el) => el.classList.contains('uppercase'));
      expect(badge).toBeDefined();
      expect(badge?.className).toContain(t.color);
    });
  });

  it('assigns correct colors for orchestration event types', async () => {
    setupFileList();
    render(<TraceStudio />);
    await waitFor(() => expect(screen.getByText('trace-2026-03-28')).toBeInTheDocument());

    const types = [
      { type: 'MANAGER_CYCLE_STARTED', color: 'text-violet' },
      { type: 'FACT_PUBLISHED', color: 'text-teal' },
      { type: 'WORKTREE_CREATED', color: 'text-pink' },
      { type: 'REALITY_ANCHOR_STARTED', color: 'text-lime' },
      { type: 'FINGERPRINT_CACHE_HIT', color: 'text-sky' },
      { type: 'COLD_STORAGE_WRITTEN', color: 'text-stone' },
    ];

    await selectFile('trace-2026-03-28.jsonl',
      types.map((t, i) => ev({
        type: t.type, timestamp: 1000 + i * 100,
        spanId: 'orch-' + i, content: 'O-' + t.type,
      })),
    );

    await waitFor(() => {
      expect(screen.getByText('O-MANAGER_CYCLE_STARTED')).toBeInTheDocument();
    });

    types.forEach((t) => {
      const label = t.type
        .replace(/_STARTED$/, '')
        .replace(/_PUBLISHED$/, '')
        .replace(/_CREATED$/, '')
        .replace(/_HIT$/, '')
        .replace(/_WRITTEN$/, '');
      const badges = screen.getAllByText(label);
      const badge = badges.find((el) => el.classList.contains('uppercase'));
      expect(badge).toBeDefined();
      expect(badge?.className).toContain(t.color);
    });
  });

  it('strips new type suffixes correctly', async () => {
    setupFileList();
    render(<TraceStudio />);
    await waitFor(() => expect(screen.getByText('trace-2026-03-28')).toBeInTheDocument());

    await selectFile('trace-2026-03-28.jsonl', [
      ev({ type: 'MANAGER_GRAPH_CONVERGED', timestamp: 1000, spanId: 'conv1', content: 'All passed' }),
      ev({ type: 'FACT_SUPERSEDED', timestamp: 2000, spanId: 'sup1', content: 'Old fact' }),
      ev({ type: 'WORKTREE_MERGE_CONFLICT', timestamp: 3000, spanId: 'mc1', content: 'Conflict in file.java' }),
    ]);

    await waitFor(() => {
      expect(screen.getByText('All passed')).toBeInTheDocument();
      // Suffixes should be stripped
      expect(screen.getByText('MANAGER_GRAPH')).toBeInTheDocument();
      expect(screen.getByText('FACT')).toBeInTheDocument();
      expect(screen.getByText('WORKTREE_MERGE')).toBeInTheDocument();
      // Full types should not appear as labels
      expect(screen.queryByText('MANAGER_GRAPH_CONVERGED')).not.toBeInTheDocument();
      expect(screen.queryByText('FACT_SUPERSEDED')).not.toBeInTheDocument();
    });
  });

  it('shows Converged badge for MANAGER_GRAPH_CONVERGED', async () => {
    setupFileList();
    render(<TraceStudio />);
    await waitFor(() => expect(screen.getByText('trace-2026-03-28')).toBeInTheDocument());

    await selectFile('trace-2026-03-28.jsonl', [
      ev({ type: 'MANAGER_GRAPH_CONVERGED', timestamp: 1000, spanId: 'c1', content: 'Done' }),
    ]);

    await waitFor(() => {
      expect(screen.getByText('Converged')).toBeInTheDocument();
    });
  });

  it('shows Stalled badge for MANAGER_GRAPH_STALLED', async () => {
    setupFileList();
    render(<TraceStudio />);
    await waitFor(() => expect(screen.getByText('trace-2026-03-28')).toBeInTheDocument());

    await selectFile('trace-2026-03-28.jsonl', [
      ev({ type: 'MANAGER_GRAPH_STALLED', timestamp: 1000, spanId: 's1', content: 'No progress' }),
    ]);

    await waitFor(() => {
      expect(screen.getByText('Stalled')).toBeInTheDocument();
    });
  });

  it('shows Superseded badge and strikethrough for FACT_SUPERSEDED', async () => {
    setupFileList();
    render(<TraceStudio />);
    await waitFor(() => expect(screen.getByText('trace-2026-03-28')).toBeInTheDocument());

    await selectFile('trace-2026-03-28.jsonl', [
      ev({ type: 'FACT_SUPERSEDED', timestamp: 1000, spanId: 'fs1', content: 'Old finding' }),
    ]);

    await waitFor(() => {
      expect(screen.getByText('Superseded')).toBeInTheDocument();
      const content = screen.getByText('Old finding');
      expect(content.className).toContain('line-through');
    });
  });

  it('shows Cached badge for FINGERPRINT_CACHE_HIT', async () => {
    setupFileList();
    render(<TraceStudio />);
    await waitFor(() => expect(screen.getByText('trace-2026-03-28')).toBeInTheDocument());

    await selectFile('trace-2026-03-28.jsonl', [
      ev({ type: 'FINGERPRINT_CACHE_HIT', timestamp: 1000, spanId: 'fch1', content: 'Node n1' }),
    ]);

    await waitFor(() => {
      expect(screen.getByText('Cached')).toBeInTheDocument();
    });
  });
});
