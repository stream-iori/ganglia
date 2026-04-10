import { describe, it, expect, beforeEach } from 'vitest';
import { usePipelineStore } from '../pipeline';

describe('pipelineStore', () => {
  beforeEach(() => {
    usePipelineStore.getState().reset();
  });

  it('starts with IDLE status', () => {
    const state = usePipelineStore.getState();
    expect(state.status).toBe('IDLE');
    expect(state.currentPhase).toBeNull();
    expect(state.ticker).toBe('');
    expect(state.result).toBeNull();
  });

  it('setTicker updates ticker', () => {
    usePipelineStore.getState().setTicker('AAPL');
    expect(usePipelineStore.getState().ticker).toBe('AAPL');
  });

  it('setPhase updates phase status', () => {
    usePipelineStore.getState().setPhase('PERCEPTION', 'RUNNING');
    const state = usePipelineStore.getState();
    expect(state.currentPhase).toBe('PERCEPTION');
    expect(state.phases.PERCEPTION.status).toBe('RUNNING');
    expect(state.status).toBe('RUNNING');
  });

  it('setResult stores data and marks all complete', () => {
    const result = {
      signal: 'BUY',
      confidence: 0.85,
      rationale: 'Strong trend',
      perceptionReport: 'Report',
      debateReport: 'Debate',
      riskReport: 'Risk',
    };
    usePipelineStore.getState().setResult(result);
    const state = usePipelineStore.getState();
    expect(state.result).toEqual(result);
    expect(state.status).toBe('COMPLETED');
    expect(state.phases.PERCEPTION.status).toBe('COMPLETED');
    expect(state.phases.SIGNAL.status).toBe('COMPLETED');
  });

  it('startPipeline sets running state', () => {
    usePipelineStore.getState().startPipeline('TSLA');
    const state = usePipelineStore.getState();
    expect(state.status).toBe('RUNNING');
    expect(state.ticker).toBe('TSLA');
    expect(state.currentPhase).toBe('PERCEPTION');
    expect(state.phases.PERCEPTION.status).toBe('RUNNING');
  });

  it('reset clears all state', () => {
    usePipelineStore.getState().startPipeline('TSLA');
    usePipelineStore.getState().reset();
    const state = usePipelineStore.getState();
    expect(state.status).toBe('IDLE');
    expect(state.ticker).toBe('');
    expect(state.result).toBeNull();
  });
});
