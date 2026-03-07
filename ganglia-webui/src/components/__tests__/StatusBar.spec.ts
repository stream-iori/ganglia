import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import StatusBar from '../StatusBar.vue'
import { useSystemStore } from '../../stores/system'

// Mock EventBus service
vi.mock('../../services/eventbus', () => ({
  eventBusService: {
    send: vi.fn()
  }
}))

describe('StatusBar Component', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.useFakeTimers()
  })

  it('renders core status when connected', () => {
    const store = useSystemStore()
    store.status = 'CONNECTED'
    
    const wrapper = mount(StatusBar)
    expect(wrapper.text()).toContain('Core Status')
    expect(wrapper.find('.bg-emerald-500').exists()).toBe(true)
  })

  it('shows file synchronization tip when fileTreeUpdatedAt changes', async () => {
    const store = useSystemStore()
    const wrapper = mount(StatusBar)
    
    expect(wrapper.text()).not.toContain('File tree synchronized')
    
    store.fileTreeUpdatedAt = Date.now()
    await wrapper.vm.$nextTick()
    
    expect(wrapper.text()).toContain('File tree synchronized')
    
    // Wait for 5 seconds
    vi.advanceTimersByTime(5001)
    await wrapper.vm.$nextTick()
    
    expect(wrapper.text()).not.toContain('File tree synchronized')
  })

  it('renders warning banner when disconnected', () => {
    const store = useSystemStore()
    store.status = 'DISCONNECTED'
    
    const wrapper = mount(StatusBar)
    expect(wrapper.text()).toContain('Disconnected')
  })

  it('renders reconnecting message', () => {
    const store = useSystemStore()
    store.status = 'RECONNECTING'
    
    const wrapper = mount(StatusBar)
    expect(wrapper.text()).toContain('Reconnecting')
  })
})
