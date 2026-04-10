import { create } from 'zustand';
import type { PipelinePhase, PipelineStatus, PipelineCompletedData } from '@/types';

interface PhaseState {
  status: PipelineStatus;
}

interface PipelineState {
  status: PipelineStatus;
  currentPhase: PipelinePhase | null;
  ticker: string;
  phases: Record<PipelinePhase, PhaseState>;
  result: PipelineCompletedData | null;

  setTicker: (ticker: string) => void;
  setPhase: (phase: PipelinePhase, status: PipelineStatus) => void;
  setResult: (result: PipelineCompletedData) => void;
  startPipeline: (ticker: string) => void;
  reset: () => void;
}

const initialPhases: Record<PipelinePhase, PhaseState> = {
  PERCEPTION: { status: 'IDLE' },
  DEBATE: { status: 'IDLE' },
  RISK: { status: 'IDLE' },
  SIGNAL: { status: 'IDLE' },
};

export const usePipelineStore = create<PipelineState>((set) => ({
  status: 'IDLE',
  currentPhase: null,
  ticker: '',
  phases: { ...initialPhases },
  result: null,

  setTicker: (ticker) => set({ ticker }),

  setPhase: (phase, status) =>
    set((state) => ({
      currentPhase: phase,
      status: status === 'COMPLETED' && phase === 'SIGNAL' ? 'COMPLETED' : 'RUNNING',
      phases: {
        ...state.phases,
        [phase]: { status },
      },
    })),

  setResult: (result) =>
    set({
      status: 'COMPLETED',
      result,
      phases: {
        PERCEPTION: { status: 'COMPLETED' },
        DEBATE: { status: 'COMPLETED' },
        RISK: { status: 'COMPLETED' },
        SIGNAL: { status: 'COMPLETED' },
      },
    }),

  startPipeline: (ticker) =>
    set({
      status: 'RUNNING',
      ticker,
      currentPhase: 'PERCEPTION',
      result: null,
      phases: {
        PERCEPTION: { status: 'RUNNING' },
        DEBATE: { status: 'IDLE' },
        RISK: { status: 'IDLE' },
        SIGNAL: { status: 'IDLE' },
      },
    }),

  reset: () =>
    set({
      status: 'IDLE',
      currentPhase: null,
      ticker: '',
      phases: { ...initialPhases },
      result: null,
    }),
}));
