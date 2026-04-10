import { create } from 'zustand';
import type { TradingConfig } from '@/types';

const defaultConfig: TradingConfig = {
  investmentStyle: 'VALUE',
  maxDebateRounds: 3,
  maxRiskDiscussRounds: 2,
  outputLanguage: 'en',
  instrumentContext: 'stock',
  dataVendor: 'YFINANCE',
  enableMemoryTwr: true,
  memoryHalfLifeDays: 180,
};

interface ConfigState {
  config: TradingConfig;
  isDirty: boolean;

  setConfig: (config: TradingConfig) => void;
  updateField: <K extends keyof TradingConfig>(key: K, value: TradingConfig[K]) => void;
  resetDirty: () => void;
}

export const useConfigStore = create<ConfigState>((set) => ({
  config: { ...defaultConfig },
  isDirty: false,

  setConfig: (config) => set({ config, isDirty: false }),

  updateField: (key, value) =>
    set((state) => ({
      config: { ...state.config, [key]: value },
      isDirty: true,
    })),

  resetDirty: () => set({ isDirty: false }),
}));
