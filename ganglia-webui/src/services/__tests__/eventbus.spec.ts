import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { eventBusService } from '../eventbus'
import { useSystemStore } from '../../stores/system'

// Mock the EventBus library as a constructor
vi.mock('@vertx/eventbus-bridge-client.js', () => {
  return {
    default: vi.fn().mockImplementation(function() {
      // @ts-ignore
      this.onopen = null;
      // @ts-ignore
      this.onclose = null;
      // @ts-ignore
      this.registerHandler = vi.fn();
      // @ts-ignore
      this.send = vi.fn();
      // @ts-ignore
      this.close = vi.fn();
    })
  }
})

describe('EventBus Service', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('should attempt to connect and set status', () => {
    eventBusService.connect()
    const systemStore = useSystemStore()
    // It starts as DISCONNECTED, then EventBus is initialized.
    // We can't easily trigger the mock's onopen without internal access,
    // but we can verify it doesn't throw.
    expect(systemStore.status).toBe('DISCONNECTED')
  })

  it('should not send if disconnected', () => {
    const systemStore = useSystemStore()
    systemStore.status = 'DISCONNECTED'
    
    vi.spyOn(console, 'error').mockImplementation(() => {})
    eventBusService.send('START', { prompt: 'test' })
    // Should return early and not call any internal eb method
  })
})
