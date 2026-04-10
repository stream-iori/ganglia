import { create } from 'zustand';
import type { SignalHistoryEntry } from '@/types';

interface SignalState {
  history: SignalHistoryEntry[];
  latestSignal: SignalHistoryEntry | null;

  addSignal: (entry: SignalHistoryEntry) => void;
  setHistory: (history: SignalHistoryEntry[]) => void;
  clear: () => void;
}

export const useSignalStore = create<SignalState>((set) => ({
  history: [],
  latestSignal: null,

  addSignal: (entry) =>
    set((state) => ({
      history: [...state.history, entry],
      latestSignal: entry,
    })),

  setHistory: (history) =>
    set({
      history,
      latestSignal: history.length > 0 ? history[history.length - 1] : null,
    }),

  clear: () => set({ history: [], latestSignal: null }),
}));
