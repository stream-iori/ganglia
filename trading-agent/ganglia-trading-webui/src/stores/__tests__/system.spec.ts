import { describe, it, expect, beforeEach } from 'vitest';
import { useSystemStore } from '../system';

describe('systemStore', () => {
  beforeEach(() => {
    useSystemStore.getState().setConnectionStatus('disconnected');
  });

  it('starts disconnected', () => {
    expect(useSystemStore.getState().connectionStatus).toBe('disconnected');
  });

  it('connection status transitions', () => {
    useSystemStore.getState().setConnectionStatus('connecting');
    expect(useSystemStore.getState().connectionStatus).toBe('connecting');
    useSystemStore.getState().setConnectionStatus('connected');
    expect(useSystemStore.getState().connectionStatus).toBe('connected');
  });

  it('sessionId is set', () => {
    expect(useSystemStore.getState().sessionId).toBeTruthy();
  });

  it('setSessionId updates id', () => {
    useSystemStore.getState().setSessionId('new-session');
    expect(useSystemStore.getState().sessionId).toBe('new-session');
  });

  it('toggleTheme switches between dark and light', () => {
    const initial = useSystemStore.getState().theme;
    useSystemStore.getState().toggleTheme();
    const toggled = useSystemStore.getState().theme;
    expect(toggled).not.toBe(initial);
    useSystemStore.getState().toggleTheme();
    expect(useSystemStore.getState().theme).toBe(initial);
  });
});
