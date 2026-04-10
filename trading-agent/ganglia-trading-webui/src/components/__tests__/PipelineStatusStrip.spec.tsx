import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { PipelineStatusStrip } from '../PipelineStatusStrip';
import { usePipelineStore } from '@/stores/pipeline';

describe('PipelineStatusStrip', () => {
  beforeEach(() => {
    usePipelineStore.getState().reset();
  });

  it('renders 4 phases', () => {
    render(<PipelineStatusStrip />);
    expect(screen.getByText('Perception')).toBeInTheDocument();
    expect(screen.getByText('Research Debate')).toBeInTheDocument();
    expect(screen.getByText('Risk Debate')).toBeInTheDocument();
    expect(screen.getByText('Signal Extraction')).toBeInTheDocument();
  });

  it('shows running state for active phase', () => {
    usePipelineStore.getState().setPhase('PERCEPTION', 'RUNNING');
    const { container } = render(<PipelineStatusStrip />);
    // The active phase should have blue border
    const activePhase = container.querySelector('.border-blue-500\\/50');
    expect(activePhase).toBeTruthy();
  });

  it('shows completed state', () => {
    usePipelineStore.getState().setPhase('PERCEPTION', 'COMPLETED');
    const { container } = render(<PipelineStatusStrip />);
    const completedPhase = container.querySelector('.border-emerald-500\\/30');
    expect(completedPhase).toBeTruthy();
  });
});
