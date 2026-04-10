import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { SignalCard } from '../SignalCard';
import type { SignalHistoryEntry } from '@/types';

describe('SignalCard', () => {
  it('shows "No signal" when signal is null', () => {
    render(<SignalCard signal={null} />);
    expect(screen.getByText('No signal')).toBeInTheDocument();
  });

  it('renders signal type', () => {
    const signal: SignalHistoryEntry = {
      ticker: 'AAPL',
      signal: 'BUY',
      confidence: 0.85,
      rationale: 'Strong momentum',
      timestamp: Date.now(),
    };
    render(<SignalCard signal={signal} />);
    expect(screen.getByText('BUY')).toBeInTheDocument();
  });

  it('shows confidence percentage', () => {
    const signal: SignalHistoryEntry = {
      ticker: 'TSLA',
      signal: 'HOLD',
      confidence: 0.5,
      rationale: 'Neutral outlook',
      timestamp: Date.now(),
    };
    render(<SignalCard signal={signal} />);
    expect(screen.getByText('50%')).toBeInTheDocument();
  });

  it('shows rationale text', () => {
    const signal: SignalHistoryEntry = {
      ticker: 'GOOG',
      signal: 'SELL',
      confidence: 0.3,
      rationale: 'Declining revenue forecast',
      timestamp: Date.now(),
    };
    render(<SignalCard signal={signal} />);
    expect(screen.getByText('Declining revenue forecast')).toBeInTheDocument();
  });
});
