import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, act } from '@testing-library/react'
import '@testing-library/jest-dom'
import StatusBar from '../StatusBar'
import { useSystemStore } from '../../stores/system'
import { useLogStore } from '../../stores/log'

// Mock EventBus service
vi.mock('../../services/eventbus', () => ({
  eventBusService: {
    send: vi.fn()
  }
}))

describe('StatusBar Component', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    useSystemStore.setState({
      status: 'DISCONNECTED',
      fileTreeUpdatedAt: 0,
      workspacePath: 'Loading...',
      currentPhase: 'IDLE'
    })
    useLogStore.setState({ events: [], streamingMessage: '' })
  })

  it('renders core status when connected', () => {
    useSystemStore.setState({ status: 'CONNECTED' })
    
    render(<StatusBar />)
    expect(screen.getByText('Core Status')).toBeInTheDocument()
    // The connected dot should have bg-emerald-500
    const dot = document.querySelector('.bg-emerald-500')
    expect(dot).toBeInTheDocument()
  })

  it('shows file synchronization tip when fileTreeUpdatedAt changes', async () => {
    const { rerender } = render(<StatusBar />)
    
    expect(screen.queryByText('File tree synchronized')).not.toBeInTheDocument()
    
    act(() => {
      useSystemStore.setState({ fileTreeUpdatedAt: Date.now() })
    })
    
    rerender(<StatusBar />)
    expect(screen.getByText('File tree synchronized')).toBeInTheDocument()
    
    // Wait for 5 seconds
    act(() => {
      vi.advanceTimersByTime(5001)
    })
    
    rerender(<StatusBar />)
    expect(screen.queryByText('File tree synchronized')).not.toBeInTheDocument()
  })

  it('renders warning banner when disconnected', () => {
    useSystemStore.setState({ status: 'DISCONNECTED' })
    
    render(<StatusBar />)
    expect(screen.getByText('Disconnected')).toBeInTheDocument()
  })

  it('renders reconnecting message', () => {
    useSystemStore.setState({ status: 'RECONNECTING' })
    
    render(<StatusBar />)
    expect(screen.getByText('Reconnecting to Ganglia Core...')).toBeInTheDocument()
  })
})
