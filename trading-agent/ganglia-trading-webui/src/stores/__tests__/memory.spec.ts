import { describe, it, expect, beforeEach } from 'vitest';
import { useMemoryStore } from '../memory';
import type { FactData } from '@/types';

const mockFact: FactData = {
  id: 'fact-1',
  managerId: 'bull',
  summary: 'Bullish signal detected',
  cycleNumber: 1,
  status: 'ACTIVE',
  tags: { role: 'bull' },
  createdAt: Date.now(),
};

describe('memoryStore', () => {
  beforeEach(() => {
    const store = useMemoryStore.getState();
    store.setFacts([]);
    store.selectFact(null);
    store.setRoleFilter('');
    store.setStatusFilter('');
  });

  it('setFacts replaces facts', () => {
    useMemoryStore.getState().setFacts([mockFact]);
    expect(useMemoryStore.getState().facts).toHaveLength(1);
    useMemoryStore.getState().setFacts([]);
    expect(useMemoryStore.getState().facts).toHaveLength(0);
  });

  it('selectFact sets selected and clears detail', () => {
    useMemoryStore.getState().setFactDetail('some detail');
    useMemoryStore.getState().selectFact(mockFact);
    const state = useMemoryStore.getState();
    expect(state.selectedFact).toEqual(mockFact);
    expect(state.factDetail).toBeNull();
  });

  it('setFactDetail updates detail', () => {
    useMemoryStore.getState().setFactDetail('full detail text');
    expect(useMemoryStore.getState().factDetail).toBe('full detail text');
  });

  it('setRoleFilter updates filter', () => {
    useMemoryStore.getState().setRoleFilter('bull');
    expect(useMemoryStore.getState().roleFilter).toBe('bull');
  });

  it('setStatusFilter updates filter', () => {
    useMemoryStore.getState().setStatusFilter('ACTIVE');
    expect(useMemoryStore.getState().statusFilter).toBe('ACTIVE');
  });
});
