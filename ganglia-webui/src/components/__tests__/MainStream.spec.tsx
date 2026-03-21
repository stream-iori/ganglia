import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, act, fireEvent } from '@testing-library/react';
import '@testing-library/jest-dom';
import MainStream from '../MainStream';
import { useLogStore } from '../../stores/log';
import type { ServerEvent } from '../../types';

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
    useLogStore.setState({
      events: [],
      streamingMessage: '',
      streamingThought: '',
    });
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
      Object.defineProperty(container, 'scrollTop', { value: 0, configurable: true });
      Object.defineProperty(container, 'scrollHeight', { value: 1000, configurable: true });
      Object.defineProperty(container, 'clientHeight', { value: 500, configurable: true });
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
      Object.defineProperty(container, 'scrollTop', { value: 500, configurable: true });
      fireEvent.scroll(container!);
    });

    // Toast should be hidden again
    expect(screen.getByText('New Messages').closest('div')).toHaveClass('opacity-0');
  });
});
