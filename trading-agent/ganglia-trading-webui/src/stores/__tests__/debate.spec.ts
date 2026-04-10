import { describe, it, expect, beforeEach } from 'vitest';
import { useDebateStore } from '../debate';
import type { FactData } from '@/types';

const mockFact: FactData = {
  id: 'fact-1',
  managerId: 'bull',
  summary: 'Bullish signal',
  cycleNumber: 1,
  status: 'ACTIVE',
  tags: { role: 'bull', stance: 'bullish' },
  createdAt: Date.now(),
};

describe('debateStore', () => {
  beforeEach(() => {
    useDebateStore.getState().reset();
  });

  it('starts with IDLE status', () => {
    const state = useDebateStore.getState();
    expect(state.researchDebate.status).toBe('IDLE');
    expect(state.riskDebate.status).toBe('IDLE');
  });

  it('setCycle updates cycle info for RESEARCH', () => {
    useDebateStore.getState().setCycle('RESEARCH', 2, 5);
    const state = useDebateStore.getState();
    expect(state.researchDebate.currentCycle).toBe(2);
    expect(state.researchDebate.maxCycles).toBe(5);
    expect(state.researchDebate.status).toBe('RUNNING');
  });

  it('setCycle updates cycle info for RISK', () => {
    useDebateStore.getState().setCycle('RISK', 1, 3);
    const state = useDebateStore.getState();
    expect(state.riskDebate.currentCycle).toBe(1);
    expect(state.riskDebate.maxCycles).toBe(3);
  });

  it('setStatus updates status with decision type', () => {
    useDebateStore.getState().setStatus('RESEARCH', 'CONVERGED', 'CONVERGED');
    const state = useDebateStore.getState();
    expect(state.researchDebate.status).toBe('CONVERGED');
    expect(state.researchDebate.decisionType).toBe('CONVERGED');
  });

  it('addFact appends to correct debate type', () => {
    useDebateStore.getState().addFact('RESEARCH', mockFact);
    const state = useDebateStore.getState();
    expect(state.researchDebate.facts).toHaveLength(1);
    expect(state.researchDebate.facts[0].id).toBe('fact-1');
    expect(state.riskDebate.facts).toHaveLength(0);
  });

  it('supersedeFact marks fact as SUPERSEDED in both debates', () => {
    useDebateStore.getState().addFact('RESEARCH', mockFact);
    useDebateStore.getState().supersedeFact('fact-1');
    const state = useDebateStore.getState();
    expect(state.researchDebate.facts[0].status).toBe('SUPERSEDED');
  });

  it('reset clears both debates', () => {
    useDebateStore.getState().addFact('RESEARCH', mockFact);
    useDebateStore.getState().setCycle('RISK', 2, 3);
    useDebateStore.getState().reset();
    const state = useDebateStore.getState();
    expect(state.researchDebate.facts).toHaveLength(0);
    expect(state.riskDebate.currentCycle).toBe(0);
  });
});
