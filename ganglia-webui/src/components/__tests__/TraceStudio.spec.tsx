import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom';
import TraceStudio from '../TraceStudio';

// Mock fetch
const mockFetch = vi.fn();
window.fetch = mockFetch;

describe('TraceStudio Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders trace files and allows selection', async () => {
    // 1. Mock the initial file list fetch
    mockFetch.mockResolvedValueOnce({
      json: async () => ['trace-2026-03-28.jsonl', 'trace-2026-03-27.jsonl'],
    });

    render(<TraceStudio />);

    // Check if files are loaded (extensions are stripped in UI)
    await waitFor(() => {
      expect(screen.getByText('trace-2026-03-28')).toBeInTheDocument();
      expect(screen.getByText('trace-2026-03-27')).toBeInTheDocument();
    });

    // 2. Mock the fetch for a specific trace file
    mockFetch.mockResolvedValueOnce({
      json: async () => [
        {
          type: 'SESSION_STARTED',
          timestamp: 1711620000000,
          spanId: 's1',
          content: 'Initial prompt',
          data: { sessionId: 's1' },
        },
        {
          type: 'TOOL_STARTED',
          timestamp: 1711620001000,
          spanId: 't1',
          parentSpanId: 's1',
          content: 'read_file',
          data: { path: 'test.txt' },
        },
      ],
    });

    // Click on a file
    fireEvent.click(screen.getByText('trace-2026-03-28'));

    // Verify events are rendered
    await waitFor(() => {
      expect(screen.getByText('SESSION')).toBeInTheDocument();
      expect(screen.getByText('Initial prompt')).toBeInTheDocument();
      expect(screen.getByText('TOOL')).toBeInTheDocument();
      expect(screen.getByText('read_file')).toBeInTheDocument();
    });

    // Check if data is rendered (now rendered as key-value pairs)
    expect(screen.getByText(/sessionId/i)).toBeInTheDocument();
    expect(screen.getAllByText(/s1/i).length).toBeGreaterThan(0);
    expect(screen.getByText(/path/i)).toBeInTheDocument();
    expect(screen.getByText(/test\.txt/i)).toBeInTheDocument();
  });

  it('shows empty state when no file is selected', () => {
    mockFetch.mockResolvedValueOnce({ json: async () => [] });
    render(<TraceStudio />);
    expect(screen.getByText(/Select a trace session/i)).toBeInTheDocument();
  });
});
