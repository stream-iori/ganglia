import { describe, it, expect, beforeEach } from 'vitest';
import { useSignalStore } from '../signals';
import type { SignalHistoryEntry } from '@/types';

const mockSignal: SignalHistoryEntry = {
  ticker: 'AAPL',
  signal: 'BUY',
  confidence: 0.85,
  rationale: 'Strong momentum',
  timestamp: Date.now(),
};

describe('signalStore', () => {
  beforeEach(() => {
    useSignalStore.getState().clear();
  });

  it('starts empty', () => {
    const state = useSignalStore.getState();
    expect(state.history).toHaveLength(0);
    expect(state.latestSignal).toBeNull();
  });

  it('addSignal appends and updates latestSignal', () => {
    useSignalStore.getState().addSignal(mockSignal);
    const state = useSignalStore.getState();
    expect(state.history).toHaveLength(1);
    expect(state.latestSignal).toEqual(mockSignal);
  });

  it('setHistory bulk-sets signals', () => {
    const signals = [mockSignal, { ...mockSignal, ticker: 'TSLA', signal: 'SELL' }];
    useSignalStore.getState().setHistory(signals);
    const state = useSignalStore.getState();
    expect(state.history).toHaveLength(2);
    expect(state.latestSignal?.ticker).toBe('TSLA');
  });

  it('clear empties everything', () => {
    useSignalStore.getState().addSignal(mockSignal);
    useSignalStore.getState().clear();
    const state = useSignalStore.getState();
    expect(state.history).toHaveLength(0);
    expect(state.latestSignal).toBeNull();
  });
});
