import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import '@testing-library/jest-dom';
import AskUserForm from '../AskUserForm';
import { useSystemStore } from '../../stores/system';
import { eventBusService } from '../../services/eventbus';
import type { ServerEvent, AskUserData } from '../../types';
import { resetStores } from '../../lib/test-utils';

// Mock dependencies
vi.mock('../../services/eventbus', () => ({
  eventBusService: {
    send: vi.fn(),
  },
}));

describe('AskUserForm Component', () => {
  beforeEach(() => {
    resetStores();
    vi.clearAllMocks();
  });

  const mockEvent: ServerEvent<AskUserData> = {
    eventId: 'e1',
    timestamp: Date.now(),
    type: 'ASK_USER',
    data: {
      askId: 'ask_123',
      questions: [
        {
          question: 'What is your choice?',
          header: 'Test',
          type: 'choice',
          options: [
            { value: 'a', label: 'Option A', description: 'Desc A' },
            { value: 'b', label: 'Option B', description: 'Desc B' },
          ],
        },
      ],
    },
  };

  it('renders historical view when not active', () => {
    render(<AskUserForm event={mockEvent} />);
    // Use regex to be more flexible with emoji/spacing
    expect(screen.getByText(/Historical Ask/i)).toBeInTheDocument();
    expect(screen.queryByText('Authorization Required')).not.toBeInTheDocument();
  });

  it('renders modal when active', () => {
    useSystemStore.setState({ activeAskId: 'ask_123' });
    render(<AskUserForm event={mockEvent} />);
    expect(screen.getByText('Authorization Required')).toBeInTheDocument();
    expect(screen.getByText('What is your choice?')).toBeInTheDocument();
    expect(screen.getByText('Option A')).toBeInTheDocument();
  });

  it('closes modal and sends response on selection', () => {
    useSystemStore.setState({ activeAskId: 'ask_123' });
    render(<AskUserForm event={mockEvent} />);

    // Click Option A
    fireEvent.click(screen.getByText('Option A'));

    // Click Confirm
    fireEvent.click(screen.getByText('Confirm Selection'));

    // Check if activeAskId is cleared (this is what we fixed!)
    expect(useSystemStore.getState().activeAskId).toBeNull();

    // Check if event was sent
    expect(eventBusService.send).toHaveBeenCalledWith(
      'RESPOND_ASK',
      expect.objectContaining({
        askId: 'ask_123',
        answers: ['a'],
      }),
    );
  });
});
