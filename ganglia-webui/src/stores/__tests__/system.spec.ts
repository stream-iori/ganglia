import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useSystemStore } from '../system'
import { useLogStore } from '../log'

describe('System Store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('should initialize with default state', () => {
    const store = useSystemStore()
    expect(store.status).toBe('DISCONNECTED')
    expect(store.isInspectorOpen).toBe(false)
    expect(store.inspectorMode).toBe('TERMINAL')
  })

  it('should set connection status', () => {
    const store = useSystemStore()
    store.setStatus('CONNECTED')
    expect(store.status).toBe('CONNECTED')
  })

  it('should toggle inspector', () => {
    const store = useSystemStore()
    
    // Open
    store.toggleInspector('call_1')
    expect(store.isInspectorOpen).toBe(true)
    expect(store.inspectorToolCallId).toBe('call_1')
    
    // Toggle off with same ID
    store.toggleInspector('call_1')
    expect(store.isInspectorOpen).toBe(false)
    
    // Switch ID and keep open
    store.toggleInspector('call_1')
    store.toggleInspector('call_2')
    expect(store.isInspectorOpen).toBe(true)
    expect(store.inspectorToolCallId).toBe('call_2')
  })

  it('should open file inspector', () => {
    const store = useSystemStore()
    store.toggleFileInspector('src/Main.java')
    expect(store.isInspectorOpen).toBe(true)
    expect(store.inspectorMode).toBe('CODE')
    expect(store.inspectFile).toBe('src/Main.java')
  })

  it('should close inspector', () => {
    const store = useSystemStore()
    store.isInspectorOpen = true
    store.closeInspector()
    expect(store.isInspectorOpen).toBe(false)
  })

  it('should handle context injection', () => {
    const store = useSystemStore()
    store.addContextToPrompt('src/test.ts')
    expect(store.pendingContextPath).toBe('src/test.ts')
    
    store.clearPendingContext()
    expect(store.pendingContextPath).toBe(null)
  })

  it('should derive modified paths from logStore', () => {
    const systemStore = useSystemStore()
    const logStore = useLogStore()
    
    // Initial state
    expect(systemStore.modifiedPaths.size).toBe(0)
    
    // Add a write_file event
    logStore.addEvent({
      eventId: '1',
      timestamp: Date.now(),
      type: 'TOOL_START',
      data: {
        toolCallId: 'c1',
        toolName: 'write_file',
        command: 'src/app.ts content...'
      }
    })
    
    expect(systemStore.modifiedPaths.has('src/app.ts')).toBe(true)
    
    // Add a replace event
    logStore.addEvent({
      eventId: '2',
      timestamp: Date.now(),
      type: 'TOOL_START',
      data: {
        toolCallId: 'c2',
        toolName: 'replace',
        command: '"src/index.html" old new'
      }
    })
    
    expect(systemStore.modifiedPaths.has('src/index.html')).toBe(true)
    expect(systemStore.modifiedPaths.size).toBe(2)
  })

  it('should manage session history', () => {
    const store = useSystemStore()
    const initialId = store.sessionId
    
    expect(store.sessionHistory).toContain(initialId)
    
    store.setSessionId('new-session')
    expect(store.sessionId).toBe('new-session')
    expect(store.sessionHistory).toContain('new-session')
    expect(store.sessionHistory[0]).toBe('new-session')
  })

  it('should reload on session switch', () => {
    const store = useSystemStore()
    const reloadSpy = vi.fn()
    // @ts-ignore
    delete window.location
    // @ts-ignore
    window.location = { reload: reloadSpy }
    
    store.switchSession('other')
    expect(store.sessionId).toBe('other')
    expect(reloadSpy).toHaveBeenCalled()
  })
})
