import { describe, it, expect, beforeEach } from 'vitest';
import { setupMockMode, runMockPipeline } from '../eventbus';
import { usePipelineStore } from '@/stores/pipeline';
import { useConfigStore } from '@/stores/config';
import { useSystemStore } from '@/stores/system';
import { useSignalStore } from '@/stores/signals';
import { useDebateStore } from '@/stores/debate';

describe('eventbus', () => {
  beforeEach(() => {
    usePipelineStore.getState().reset();
    useDebateStore.getState().reset();
    useSignalStore.getState().clear();
    useSystemStore.getState().setConnectionStatus('disconnected');
  });

  describe('setupMockMode', () => {
    it('sets connected status when mock=true', () => {
      // Mock the URL
      Object.defineProperty(window, 'location', {
        value: { ...window.location, search: '?mock=true' },
        writable: true,
      });

      setupMockMode();
      expect(useSystemStore.getState().connectionStatus).toBe('connected');
    });

    it('sets config', () => {
      Object.defineProperty(window, 'location', {
        value: { ...window.location, search: '?mock=true' },
        writable: true,
      });

      setupMockMode();
      expect(useConfigStore.getState().config.investmentStyle).toBe('VALUE');
    });
  });

  describe('runMockPipeline', () => {
    it('starts pipeline with ticker', () => {
      runMockPipeline('AAPL');
      const state = usePipelineStore.getState();
      expect(state.status).toBe('RUNNING');
      expect(state.ticker).toBe('AAPL');
    });
  });
});
