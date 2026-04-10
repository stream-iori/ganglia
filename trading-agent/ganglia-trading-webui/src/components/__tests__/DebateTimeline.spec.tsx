import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { DebateTimeline } from '../DebateTimeline';

describe('DebateTimeline', () => {
  const cycles = [
    { cycleNumber: 1, status: 'completed' as const },
    { cycleNumber: 2, status: 'running' as const },
    { cycleNumber: 3, status: 'pending' as const },
  ];

  it('renders cycle nodes', () => {
    render(<DebateTimeline cycles={cycles} />);
    expect(screen.getByText('Cycle 1')).toBeInTheDocument();
    expect(screen.getByText('Cycle 2')).toBeInTheDocument();
    expect(screen.getByText('Cycle 3')).toBeInTheDocument();
  });

  it('shows decision badge for converged cycle', () => {
    const cyclesWithDecision = [
      { cycleNumber: 1, status: 'completed' as const, decisionType: 'CONVERGED' },
    ];
    render(<DebateTimeline cycles={cyclesWithDecision} />);
    expect(screen.getByText('Converged')).toBeInTheDocument();
  });

  it('fires callback on click', () => {
    const onClick = vi.fn();
    render(<DebateTimeline cycles={cycles} onCycleClick={onClick} />);
    fireEvent.click(screen.getByText('Cycle 2'));
    expect(onClick).toHaveBeenCalledWith(2);
  });
});
