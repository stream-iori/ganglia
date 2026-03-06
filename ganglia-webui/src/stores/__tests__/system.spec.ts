import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useSystemStore } from '../system'

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
})
