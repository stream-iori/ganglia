import { describe, it, expect, beforeEach, vi } from 'vitest';
import { useSystemStore } from '../system';
import { useLogStore } from '../log';

describe('System Store', () => {
  beforeEach(() => {
    // Reset Zustand stores
    useSystemStore.setState({
      status: 'DISCONNECTED',
      isInspectorOpen: false,
      inspectorMode: 'TERMINAL',
      inspectorToolCallId: null,
      inspectFile: null,
      inspectDiff: null,
      pendingContextPath: null,
      sessionId: 'test-session',
      sessionHistory: ['test-session'],
    });
    useLogStore.setState({ events: [] });
  });

  it('should initialize with default state', () => {
    const state = useSystemStore.getState();
    expect(state.status).toBe('DISCONNECTED');
    expect(state.isInspectorOpen).toBe(false);
    expect(state.inspectorMode).toBe('TERMINAL');
  });

  it('should set connection status', () => {
    const store = useSystemStore.getState();
    store.setStatus('CONNECTED');
    expect(useSystemStore.getState().status).toBe('CONNECTED');
  });

  it('should toggle inspector', () => {
    const store = useSystemStore.getState();

    // Open
    store.toggleInspector('call_1');
    expect(useSystemStore.getState().isInspectorOpen).toBe(true);
    expect(useSystemStore.getState().inspectorToolCallId).toBe('call_1');

    // Toggle off with same ID
    store.toggleInspector('call_1');
    expect(useSystemStore.getState().isInspectorOpen).toBe(false);

    // Switch ID and keep open
    store.toggleInspector('call_1');
    store.toggleInspector('call_2');
    expect(useSystemStore.getState().isInspectorOpen).toBe(true);
    expect(useSystemStore.getState().inspectorToolCallId).toBe('call_2');
  });

  it('should open file inspector', () => {
    const store = useSystemStore.getState();
    store.toggleFileInspector('src/Main.java');
    expect(useSystemStore.getState().isInspectorOpen).toBe(true);
    expect(useSystemStore.getState().inspectorMode).toBe('CODE');
    expect(useSystemStore.getState().inspectFile).toBe('src/Main.java');
  });

  it('should close inspector', () => {
    const store = useSystemStore.getState();
    useSystemStore.setState({ isInspectorOpen: true });
    store.closeInspector();
    expect(useSystemStore.getState().isInspectorOpen).toBe(false);
  });

  it('should handle context injection', () => {
    const store = useSystemStore.getState();
    store.addContextToPrompt('src/test.ts');
    expect(useSystemStore.getState().pendingContextPath).toBe('src/test.ts');

    store.clearPendingContext();
    expect(useSystemStore.getState().pendingContextPath).toBe(null);
  });

  it('should derive modified paths from logStore', () => {
    const systemStore = useSystemStore.getState();

    // Initial state
    expect(systemStore.getModifiedPaths().size).toBe(0);

    // Add a write_file event
    useLogStore.getState().addEvent({
      eventId: '1',
      timestamp: Date.now(),
      type: 'TOOL_START',
      data: {
        toolCallId: 'c1',
        toolName: 'write_file',
        command: 'src/app.ts content...',
      },
    });

    expect(useSystemStore.getState().getModifiedPaths().has('src/app.ts')).toBe(true);

    // Add a replace event
    useLogStore.getState().addEvent({
      eventId: '2',
      timestamp: Date.now(),
      type: 'TOOL_START',
      data: {
        toolCallId: 'c2',
        toolName: 'replace',
        command: '"src/index.html" old new',
      },
    });

    expect(useSystemStore.getState().getModifiedPaths().has('src/index.html')).toBe(true);
    expect(useSystemStore.getState().getModifiedPaths().size).toBe(2);
  });

  it('should manage session history', () => {
    const store = useSystemStore.getState();
    const initialId = store.sessionId;

    expect(store.sessionHistory).toContain(initialId);

    store.setSessionId('new-session');
    expect(useSystemStore.getState().sessionId).toBe('new-session');
    expect(useSystemStore.getState().sessionHistory).toContain('new-session');
    expect(useSystemStore.getState().sessionHistory[0]).toBe('new-session');
  });

  it('should reload on session switch', () => {
    const store = useSystemStore.getState();
    const reloadSpy = vi.fn();
    // @ts-expect-error - Testing invalid phase transition
    const oldLocation = window.location;
    // @ts-expect-error - Testing invalid phase transition
    delete window.location;
    // @ts-expect-error - Testing invalid phase transition
    window.location = { ...oldLocation, reload: reloadSpy };

    store.switchSession('other');
    expect(useSystemStore.getState().sessionId).toBe('other');
    expect(reloadSpy).toHaveBeenCalled();

    // Restore
    // @ts-expect-error - Testing invalid phase transition
    window.location = oldLocation;
  });
});
