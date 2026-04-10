import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { ConfigPanel } from '@/pages/ConfigPanel';
import { useConfigStore } from '@/stores/config';
import { useSystemStore } from '@/stores/system';

describe('ConfigPanel', () => {
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
    useSystemStore.getState().setConnectionStatus('connected');
  });

  it('renders all config fields', () => {
    render(
      <MemoryRouter>
        <ConfigPanel />
      </MemoryRouter>
    );
    expect(screen.getByText('Investment Style')).toBeInTheDocument();
    expect(screen.getByText('VALUE')).toBeInTheDocument();
    expect(screen.getByText('GROWTH')).toBeInTheDocument();
    expect(screen.getByText('Output Language')).toBeInTheDocument();
    expect(screen.getByText('Data Vendor')).toBeInTheDocument();
    expect(screen.getByText('Enable Memory TWR')).toBeInTheDocument();
  });

  it('clicking style button marks dirty', () => {
    render(
      <MemoryRouter>
        <ConfigPanel />
      </MemoryRouter>
    );
    fireEvent.click(screen.getByText('GROWTH'));
    expect(useConfigStore.getState().isDirty).toBe(true);
    expect(useConfigStore.getState().config.investmentStyle).toBe('GROWTH');
  });

  it('shows save and reset buttons', () => {
    render(
      <MemoryRouter>
        <ConfigPanel />
      </MemoryRouter>
    );
    expect(screen.getByText('Save')).toBeInTheDocument();
    expect(screen.getByText('Reset')).toBeInTheDocument();
  });
});
