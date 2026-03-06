import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useLogStore } from '../log'
import type { ServerEvent, ToolStartData } from '../../types'

describe('Log Store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('should add an event', () => {
    const store = useLogStore()
    const event: ServerEvent = {
      eventId: '1',
      timestamp: Date.now(),
      type: 'THOUGHT',
      data: { content: 'hello' }
    }
    
    store.addEvent(event)
    expect(store.events).toHaveLength(1)
    expect(store.events[0]).toEqual(event)
  })

  it('should initialize TTY map when TOOL_START is added', () => {
    const store = useLogStore()
    const toolCallId = 'call_123'
    const event: ServerEvent<ToolStartData> = {
      eventId: '2',
      timestamp: Date.now(),
      type: 'TOOL_START',
      data: {
        toolCallId,
        toolName: 'Bash',
        command: 'ls'
      }
    }
    
    store.addEvent(event)
    expect(store.activeToolCalls[toolCallId]).toBeDefined()
    expect(store.activeToolCalls[toolCallId]).toEqual([])
  })

  it('should append Tty data', () => {
    const store = useLogStore()
    const toolCallId = 'call_123'
    
    // Setup active call
    store.activeToolCalls[toolCallId] = []
    
    store.appendTty(toolCallId, 'line 1\n')
    expect(store.activeToolCalls[toolCallId]).toHaveLength(1)
    expect(store.activeToolCalls[toolCallId][0]).toBe('line 1\n')
  })

  it('should clear events', () => {
    const store = useLogStore()
    store.events = [{ eventId: '1' } as any]
    store.activeToolCalls = { '1': [] }
    
    store.clear()
    expect(store.events).toHaveLength(0)
    expect(Object.keys(store.activeToolCalls)).toHaveLength(0)
  })

  it('should handle USER_MESSAGE', () => {
    const store = useLogStore()
    const event: ServerEvent = {
      eventId: 'user-1',
      timestamp: Date.now(),
      type: 'USER_MESSAGE',
      data: { content: 'who are you' }
    }
    
    store.addEvent(event)
    expect(store.events).toHaveLength(1)
    expect(store.events[0].type).toBe('USER_MESSAGE')
  })

  it('should replace "..." placeholder when real thought arrives', () => {
    const store = useLogStore()
    
    // 1. Add placeholder
    store.addEvent({
      eventId: 'p1',
      timestamp: Date.now(),
      type: 'THOUGHT',
      data: { content: '...' }
    })
    expect(store.events).toHaveLength(1)

    // 2. Add real thought
    store.addEvent({
      eventId: 't1',
      timestamp: Date.now(),
      type: 'THOUGHT',
      data: { content: 'I am thinking about it' }
    })
    
    expect(store.events).toHaveLength(1)
    expect(store.events[0].data.content).toBe('I am thinking about it')
  })

  it('should deduplicate THOUGHT and AGENT_MESSAGE with same content', () => {
    const store = useLogStore()
    
    const content = 'Hello world'
    
    store.addEvent({
      eventId: 't1',
      timestamp: Date.now(),
      type: 'THOUGHT',
      data: { content }
    })
    
    store.addEvent({
      eventId: 'a1',
      timestamp: Date.now(),
      type: 'AGENT_MESSAGE',
      data: { content }
    })
    
    expect(store.events).toHaveLength(1)
    expect(store.events[0].type).toBe('AGENT_MESSAGE')
  })
})
