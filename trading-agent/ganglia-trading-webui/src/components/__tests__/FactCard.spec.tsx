import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { FactCard } from '../FactCard';
import type { FactData } from '@/types';

const mockFact: FactData = {
  id: 'f-1',
  managerId: 'bull',
  summary: 'Strong momentum detected',
  cycleNumber: 2,
  status: 'ACTIVE',
  tags: { role: 'bull', stance: 'bullish' },
  createdAt: Date.now(),
};

describe('FactCard', () => {
  it('renders summary', () => {
    render(<FactCard fact={mockFact} />);
    expect(screen.getByText('Strong momentum detected')).toBeInTheDocument();
  });

  it('renders role badge', () => {
    render(<FactCard fact={mockFact} />);
    expect(screen.getByText('bull')).toBeInTheDocument();
  });

  it('renders stance badge', () => {
    render(<FactCard fact={mockFact} />);
    expect(screen.getByText('bullish')).toBeInTheDocument();
  });

  it('renders cycle number', () => {
    render(<FactCard fact={mockFact} />);
    expect(screen.getByText('Cycle 2')).toBeInTheDocument();
  });

  it('applies strikethrough for superseded facts', () => {
    const superseded = { ...mockFact, status: 'SUPERSEDED' };
    const { container } = render(<FactCard fact={superseded} />);
    const summary = container.querySelector('.line-through');
    expect(summary).toBeTruthy();
  });
});
