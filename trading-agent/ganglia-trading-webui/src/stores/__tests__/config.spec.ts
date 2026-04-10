import { describe, it, expect, beforeEach } from 'vitest';
import { useConfigStore } from '../config';

describe('configStore', () => {
  beforeEach(() => {
    useConfigStore.getState().setConfig({
      investmentStyle: 'VALUE',
      maxDebateRounds: 3,
      maxRiskDiscussRounds: 2,
      outputLanguage: 'en',
      instrumentContext: 'stock',
      dataVendor: 'YFINANCE',
      enableMemoryTwr: true,
      memoryHalfLifeDays: 180,
    });
  });

  it('defaults match TradingConfig.defaults()', () => {
    const state = useConfigStore.getState();
    expect(state.config.investmentStyle).toBe('VALUE');
    expect(state.config.maxDebateRounds).toBe(3);
    expect(state.config.maxRiskDiscussRounds).toBe(2);
    expect(state.config.outputLanguage).toBe('en');
    expect(state.config.instrumentContext).toBe('stock');
    expect(state.config.dataVendor).toBe('YFINANCE');
    expect(state.config.enableMemoryTwr).toBe(true);
    expect(state.config.memoryHalfLifeDays).toBe(180);
  });

  it('setConfig sets full config and clears dirty', () => {
    useConfigStore.getState().updateField('maxDebateRounds', 5);
    expect(useConfigStore.getState().isDirty).toBe(true);
    useConfigStore.getState().setConfig({ ...useConfigStore.getState().config, maxDebateRounds: 5 });
    expect(useConfigStore.getState().isDirty).toBe(false);
  });

  it('updateField marks dirty', () => {
    useConfigStore.getState().updateField('investmentStyle', 'GROWTH');
    const state = useConfigStore.getState();
    expect(state.config.investmentStyle).toBe('GROWTH');
    expect(state.isDirty).toBe(true);
  });

  it('resetDirty clears dirty flag', () => {
    useConfigStore.getState().updateField('maxDebateRounds', 7);
    useConfigStore.getState().resetDirty();
    expect(useConfigStore.getState().isDirty).toBe(false);
  });
});
