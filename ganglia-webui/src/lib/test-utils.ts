import { useSystemStore } from '../stores/system';
import { useLogStore } from '../stores/log';
import { usePlanStore } from '../stores/plan';
import { vi } from 'vitest';

/**
 * Resets all Zustand stores to their initial states.
 * Useful in beforeEach() blocks.
 */
export function resetStores() {
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
    fileTreeUpdatedAt: 0,
    workspacePath: 'Loading...',
    currentPhase: 'IDLE',
    mcpCount: 0,
    activeAskId: null,
  });

  useLogStore.setState({
    events: [],
    activeToolCalls: {},
    fileCache: {},
    fileTree: null,
    streamingMessage: '',
    streamingThought: '',
    thoughtStartTime: null,
  });

  usePlanStore.setState({
    plan: null,
  });
}

/**
 * Helper to mock read-only DOM properties like scrollHeight in JSDOM.
 */
export function mockDomProperty(
  element: HTMLElement,
  property: string,
  value: any /* eslint-disable-line @typescript-eslint/no-explicit-any */,
) {
  Object.defineProperty(element, property, { value, configurable: true });
}

/**
 * Creates a mock EventBusService.
 */
export function createMockEventBus() {
  return {
    send: vi.fn().mockResolvedValue({ status: 'ok' }),
    close: vi.fn(),
  };
}
