import { describe, it, expect, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import '@testing-library/jest-dom'
import ToolCard from '../ToolCard'
import type { ServerEvent, ToolStartData, ToolResultData } from '../../types'
import { useSystemStore } from '../../stores/system'
import { useLogStore } from '../../stores/log'

describe('ToolCard Component', () => {
  beforeEach(() => {
    useSystemStore.setState({ isInspectorOpen: false })
    useLogStore.setState({ activeToolCalls: {} })
  })

  const startEvent: ServerEvent<ToolStartData> = {
    eventId: 'e1',
    timestamp: Date.now(),
    type: 'TOOL_START',
    data: {
      toolCallId: 'call_1',
      toolName: 'Bash',
      command: 'mvn clean'
    }
  }

  it('renders in running state initially', () => {
    render(<ToolCard event={startEvent} allEvents={[startEvent]} />)
    
    expect(screen.getByText('Bash')).toBeInTheDocument()
    expect(screen.getByText('mvn clean')).toBeInTheDocument()
    const pulseDot = document.querySelector('.animate-pulse')
    expect(pulseDot).toBeInTheDocument()
  })

  it('renders success state when result arrives', async () => {
    const resultEvent: ServerEvent<ToolResultData> = {
      eventId: 'e2',
      timestamp: Date.now() + 1000,
      type: 'TOOL_RESULT',
      data: {
        toolCallId: 'call_1',
        exitCode: 0,
        summary: 'Build Success',
        fullOutput: '...',
        isError: false
      }
    }

    render(<ToolCard event={startEvent} allEvents={[startEvent, resultEvent]} />)
    
    expect(screen.getByText('✓')).toBeInTheDocument()
    
    // It auto-collapses on success, so we need to click to see the summary
    fireEvent.click(screen.getByText('Bash'))
    
    expect(screen.getByText('Build Success')).toBeInTheDocument()
    const pulseDot = document.querySelector('.animate-pulse')
    expect(pulseDot).not.toBeInTheDocument()
  })

  it('renders error state when result fails', () => {
    const resultEvent: ServerEvent<ToolResultData> = {
      eventId: 'e2',
      timestamp: Date.now() + 1000,
      type: 'TOOL_RESULT',
      data: {
        toolCallId: 'call_1',
        exitCode: 1,
        summary: 'Build Failed',
        fullOutput: '...',
        isError: true
      }
    }

    render(<ToolCard event={startEvent} allEvents={[startEvent, resultEvent]} />)
    
    expect(screen.getByText('Build Failed')).toBeInTheDocument()
    expect(screen.getByText('✕')).toBeInTheDocument()
    
    // The parent container should have the error border
    const card = screen.getByText('Bash').closest('.rounded-lg')
    expect(card).toHaveClass('border-rose-500/50')
  })
})
