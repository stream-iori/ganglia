import { describe, it, expect, beforeEach } from 'vitest';
import { useLogStore } from '../log';
import type { ServerEvent, ToolStartData } from '../../types';

describe('Log Store', () => {
  beforeEach(() => {
    // Reset Zustand store
    useLogStore.setState({
      events: [],
      activeToolCalls: {},
      fileCache: {},
      fileTree: null,
      streamingMessage: '',
      streamingThought: '',
      thoughtStartTime: null,
    });
  });

  it('should add an event', () => {
    const store = useLogStore.getState();
    const event: ServerEvent = {
      eventId: '1',
      timestamp: Date.now(),
      type: 'THOUGHT',
      data: { content: 'hello' },
    };

    store.addEvent(event);
    const state = useLogStore.getState();
    expect(state.events).toHaveLength(1);
    expect(state.events[0]).toEqual(event);
  });

  it('should initialize TTY map when TOOL_START is added', () => {
    const store = useLogStore.getState();
    const toolCallId = 'call_123';
    const event: ServerEvent<ToolStartData> = {
      eventId: '2',
      timestamp: Date.now(),
      type: 'TOOL_START',
      data: {
        toolCallId,
        toolName: 'Bash',
        command: 'ls',
      },
    };

    store.addEvent(event);
    const state = useLogStore.getState();
    expect(state.activeToolCalls[toolCallId]).toBeDefined();
    expect(state.activeToolCalls[toolCallId]).toEqual([]);
  });

  it('should append Tty data', () => {
    const store = useLogStore.getState();
    const toolCallId = 'call_123';

    // Setup active call
    useLogStore.setState({ activeToolCalls: { [toolCallId]: [] } });

    store.appendTty(toolCallId, 'line 1\n');
    const state = useLogStore.getState();
    expect(state.activeToolCalls[toolCallId]).toHaveLength(1);
    expect(state.activeToolCalls[toolCallId][0]).toBe('line 1\n');
  });

  it('should clear events', () => {
    const store = useLogStore.getState();
    useLogStore.setState({
      events: [{ eventId: '1' } as unknown],
      activeToolCalls: { '1': [] },
    });

    store.clear();
    const state = useLogStore.getState();
    expect(state.events).toHaveLength(0);
    expect(Object.keys(state.activeToolCalls)).toHaveLength(0);
  });

  it('should handle USER_MESSAGE', () => {
    const store = useLogStore.getState();
    const event: ServerEvent = {
      eventId: 'user-1',
      timestamp: Date.now(),
      type: 'USER_MESSAGE',
      data: { content: 'who are you' } as unknown,
    };

    store.addEvent(event);
    const state = useLogStore.getState();
    expect(state.events).toHaveLength(1);
    expect(state.events[0]?.type).toBe('USER_MESSAGE');
  });

  it('should replace "..." placeholder when real thought arrives', () => {
    const store = useLogStore.getState();

    // 1. Add placeholder
    store.addEvent({
      eventId: 'p1',
      timestamp: Date.now(),
      type: 'THOUGHT',
      data: { content: '...' } as unknown,
    });
    expect(useLogStore.getState().events).toHaveLength(1);

    // 2. Add real thought
    store.addEvent({
      eventId: 't1',
      timestamp: Date.now(),
      type: 'THOUGHT',
      data: { content: 'I am thinking about it' } as unknown,
    });

    const state = useLogStore.getState();
    expect(state.events).toHaveLength(1);
    expect((state.events[0]?.data as unknown).content).toBe('I am thinking about it');
  });

  it('should deduplicate THOUGHT and AGENT_MESSAGE with same content', () => {
    const store = useLogStore.getState();

    const content = 'Hello world';

    store.addEvent({
      eventId: 't1',
      timestamp: Date.now(),
      type: 'THOUGHT',
      data: { content } as unknown,
    });

    store.addEvent({
      eventId: 'a1',
      timestamp: Date.now(),
      type: 'AGENT_MESSAGE',
      data: { content } as unknown,
    });

    const state = useLogStore.getState();
    expect(state.events).toHaveLength(1);
    expect(state.events[0]?.type).toBe('AGENT_MESSAGE');
  });

  it('should track thought duration correctly', () => {
    const store = useLogStore.getState();

    // 1. Thought starts
    store.addEvent({
      eventId: 't1',
      timestamp: Date.now(),
      type: 'THOUGHT',
      data: { content: 'Processing...' } as unknown,
    });

    expect(useLogStore.getState().thoughtStartTime).toBeDefined();

    // 2. Transition to AGENT_MESSAGE
    store.addEvent({
      eventId: 'a1',
      timestamp: Date.now(),
      type: 'AGENT_MESSAGE',
      data: { content: 'hello' } as unknown,
    });

    const state = useLogStore.getState();
    const thought = state.events.find((e) => e.eventId === 't1');
    expect((thought?.data as unknown).durationMs).toBeDefined();
  });
});
