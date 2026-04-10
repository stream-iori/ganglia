import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { NavBar } from '../NavBar';
import { useSystemStore } from '@/stores/system';

describe('NavBar', () => {
  beforeEach(() => {
    useSystemStore.getState().setConnectionStatus('connected');
  });

  it('renders all nav links', () => {
    render(
      <MemoryRouter>
        <NavBar />
      </MemoryRouter>
    );
    expect(screen.getByText('Dashboard')).toBeInTheDocument();
    expect(screen.getByText('Pipeline')).toBeInTheDocument();
    expect(screen.getByText('Debate')).toBeInTheDocument();
    expect(screen.getByText('Memory')).toBeInTheDocument();
    expect(screen.getByText('Config')).toBeInTheDocument();
    expect(screen.getByText('Trace')).toBeInTheDocument();
  });

  it('shows connection status', () => {
    render(
      <MemoryRouter>
        <NavBar />
      </MemoryRouter>
    );
    expect(screen.getByText('connected')).toBeInTheDocument();
  });

  it('shows disconnected status', () => {
    useSystemStore.getState().setConnectionStatus('disconnected');
    render(
      <MemoryRouter>
        <NavBar />
      </MemoryRouter>
    );
    expect(screen.getByText('disconnected')).toBeInTheDocument();
  });

  it('shows app title', () => {
    render(
      <MemoryRouter>
        <NavBar />
      </MemoryRouter>
    );
    expect(screen.getByText('Trading Agent')).toBeInTheDocument();
  });
});
