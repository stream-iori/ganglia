import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ToolCard from '../ToolCard.vue'
import type { ServerEvent, ToolStartData, ToolResultData } from '../../types'

describe('ToolCard Component', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
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
    const wrapper = mount(ToolCard, {
      props: {
        event: startEvent,
        allEvents: [startEvent]
      }
    })
    
    expect(wrapper.text()).toContain('Bash')
    expect(wrapper.text()).toContain('mvn clean')
    expect(wrapper.find('.animate-pulse').exists()).toBe(true)
  })

  it('renders success state when result arrives', () => {
    const resultEvent: ServerEvent<ToolResultData> = {
      eventId: 'e2',
      timestamp: Date.now(),
      type: 'TOOL_RESULT',
      data: {
        toolCallId: 'call_1',
        exitCode: 0,
        summary: 'Build Success',
        fullOutput: '...',
        isError: false
      }
    }

    const wrapper = mount(ToolCard, {
      props: {
        event: startEvent,
        allEvents: [startEvent, resultEvent]
      }
    })
    
    expect(wrapper.text()).toContain('Build Success')
    expect(wrapper.text()).toContain('✓')
    expect(wrapper.find('.animate-pulse').exists()).toBe(false)
  })

  it('renders error state when result fails', () => {
    const resultEvent: ServerEvent<ToolResultData> = {
      eventId: 'e2',
      timestamp: Date.now(),
      type: 'TOOL_RESULT',
      data: {
        toolCallId: 'call_1',
        exitCode: 1,
        summary: 'Build Failed',
        fullOutput: '...',
        isError: true
      }
    }

    const wrapper = mount(ToolCard, {
      props: {
        event: startEvent,
        allEvents: [startEvent, resultEvent]
      }
    })
    
    expect(wrapper.text()).toContain('Build Failed')
    expect(wrapper.text()).toContain('✕')
    // Check classes instead of using find with complex selector
    expect(wrapper.get('div').classes()).toContain('border-rose-500/50')
  })
})
