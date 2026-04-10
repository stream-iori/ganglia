import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, act, fireEvent } from '@testing-library/react';
import '@testing-library/jest-dom';
import MainStream from '../MainStream';
import { useLogStore } from '../../stores/log';
import type { ServerEvent } from '../../types';
import { resetStores, mockDomProperty } from '../../lib/test-utils';

// Mock scrollIntoView
window.HTMLElement.prototype.scrollIntoView = vi.fn();

// Mock dependencies
vi.mock('../../services/eventbus', () => ({
  eventBusService: {
    send: vi.fn(),
  },
}));

vi.mock('../StatusBar', () => ({
  default: () => <div data-testid="status-bar" />,
}));

vi.mock('../ThoughtCard', () => ({
  default: ({ content }: { content: string }) => <div data-testid="thought-card">{content}</div>,
}));

vi.mock('../AgentMessage', () => ({
  default: ({ content }: { content: string }) => <div data-testid="agent-message">{content}</div>,
}));

describe('MainStream Component', () => {
  beforeEach(() => {
    resetStores();
    vi.clearAllMocks();
  });

  it('renders correctly initially', () => {
    render(<MainStream />);
    expect(screen.getByTestId('status-bar')).toBeInTheDocument();
    expect(screen.getByText('Ganglia TUI Pro')).toBeInTheDocument();
  });

  it('shows "New Messages" toast only when new events arrive while scrolled up', () => {
    render(<MainStream />);

    const container = screen.getByRole('main').querySelector('.overflow-y-auto');
    expect(container).toBeTruthy();

    // 1. Initially hidden (has opacity-0)
    expect(screen.getByText('New Messages').closest('div')).toHaveClass('opacity-0');

    // 2. Scroll up (set isScrolledToBottom to false)
    act(() => {
      mockDomProperty(container as HTMLElement, 'scrollTop', 0);
      mockDomProperty(container as HTMLElement, 'scrollHeight', 1000);
      mockDomProperty(container as HTMLElement, 'clientHeight', 500);
      fireEvent.scroll(container!);
    });

    // toast should STILL be hidden (this is what we fixed!)
    expect(screen.getByText('New Messages').closest('div')).toHaveClass('opacity-0');

    // 3. New event arrives while scrolled up
    const event1: ServerEvent = {
      eventId: '1',
      type: 'AGENT_MESSAGE',
      data: { content: 'Hello' },
      timestamp: Date.now(),
    };

    act(() => {
      useLogStore.setState({ events: [event1] });
    });

    // Now toast should be visible (has opacity-100)
    expect(screen.getByText('New Messages').closest('div')).toHaveClass('opacity-100');

    // 4. Scroll back to bottom
    act(() => {
      mockDomProperty(container as HTMLElement, 'scrollTop', 500);
      fireEvent.scroll(container!);
    });

    // Toast should be hidden again
    expect(screen.getByText('New Messages').closest('div')).toHaveClass('opacity-0');
  });
});
