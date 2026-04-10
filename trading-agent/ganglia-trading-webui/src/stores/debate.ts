import { create } from 'zustand';
import type { DebateType, FactData } from '@/types';

interface DebateSubState {
  currentCycle: number;
  maxCycles: number;
  status: 'IDLE' | 'RUNNING' | 'CONVERGED' | 'STALLED';
  facts: FactData[];
  decisionType?: string;
}

interface DebateState {
  researchDebate: DebateSubState;
  riskDebate: DebateSubState;

  setCycle: (debateType: DebateType, cycleNumber: number, maxCycles: number) => void;
  setStatus: (debateType: DebateType, status: DebateSubState['status'], decisionType?: string) => void;
  addFact: (debateType: DebateType, fact: FactData) => void;
  supersedeFact: (factId: string) => void;
  reset: () => void;
}

const initialSubState: DebateSubState = {
  currentCycle: 0,
  maxCycles: 0,
  status: 'IDLE',
  facts: [],
};

export const useDebateStore = create<DebateState>((set) => ({
  researchDebate: { ...initialSubState },
  riskDebate: { ...initialSubState },

  setCycle: (debateType, cycleNumber, maxCycles) =>
    set((state) => {
      const key = debateType === 'RESEARCH' ? 'researchDebate' : 'riskDebate';
      return {
        [key]: {
          ...state[key],
          currentCycle: cycleNumber,
          maxCycles,
          status: 'RUNNING',
        },
      };
    }),

  setStatus: (debateType, status, decisionType) =>
    set((state) => {
      const key = debateType === 'RESEARCH' ? 'researchDebate' : 'riskDebate';
      return {
        [key]: {
          ...state[key],
          status,
          decisionType,
        },
      };
    }),

  addFact: (debateType, fact) =>
    set((state) => {
      const key = debateType === 'RESEARCH' ? 'researchDebate' : 'riskDebate';
      return {
        [key]: {
          ...state[key],
          facts: [...state[key].facts, fact],
        },
      };
    }),

  supersedeFact: (factId) =>
    set((state) => ({
      researchDebate: {
        ...state.researchDebate,
        facts: state.researchDebate.facts.map((f) =>
          f.id === factId ? { ...f, status: 'SUPERSEDED' } : f
        ),
      },
      riskDebate: {
        ...state.riskDebate,
        facts: state.riskDebate.facts.map((f) =>
          f.id === factId ? { ...f, status: 'SUPERSEDED' } : f
        ),
      },
    })),

  reset: () =>
    set({
      researchDebate: { ...initialSubState, facts: [] },
      riskDebate: { ...initialSubState, facts: [] },
    }),
}));
