import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { eventBusService } from '../eventbus'
import { useSystemStore } from '../../stores/system'
import { useLogStore } from '../../stores/log'

class MockWebSocket {
  static OPEN = 1;
  url: string;
  readyState: number = 0; // CONNECTING
  onopen: (() => void) | null = null;
  onclose: (() => void) | null = null;
  onmessage: ((event: { data: string }) => void) | null = null;
  onerror: ((error: any) => void) | null = null;
  
  constructor(url: string) {
    this.url = url;
    MockWebSocket.lastInstance = this;
    setTimeout(() => {
      this.readyState = 1; // OPEN
      if (this.onopen) this.onopen();
    }, 10);
  }
  
  send(data: string) {
    const msg = JSON.parse(data);
    if (msg.method === 'SYNC') {
      setTimeout(() => {
        if (this.onmessage) {
          this.onmessage({
            data: JSON.stringify({
              jsonrpc: '2.0',
              id: msg.id,
              result: { history: [] }
            })
          });
        }
      }, 5);
    }
  }
  
  close() {
    this.readyState = 3; // CLOSED
    if (this.onclose) this.onclose();
  }

  simulateMessage(obj: any) {
    if (this.onmessage) {
      this.onmessage({ data: JSON.stringify(obj) });
    }
  }

  static lastInstance: MockWebSocket | null = null;
}

describe('EventBus Service (WebSocket/JSON-RPC)', () => {
  let originalWebSocket: any;

  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    
    originalWebSocket = window.WebSocket;
    (window as any).WebSocket = MockWebSocket;
  })

  afterEach(() => {
    (window as any).WebSocket = originalWebSocket;
    MockWebSocket.lastInstance = null;
  })

  it('should connect and sync history', async () => {
    eventBusService.connect()
    const systemStore = useSystemStore()
    const logStore = useLogStore()
    
    await new Promise(resolve => setTimeout(resolve, 50));
    
    expect(systemStore.status).toBe('CONNECTED')
    expect(logStore.events).toHaveLength(0)
  })

  it('should handle server events (notifications)', async () => {
    eventBusService.connect()
    await new Promise(resolve => setTimeout(resolve, 20));
    
    const logStore = useLogStore()
    
    MockWebSocket.lastInstance?.simulateMessage({
      jsonrpc: '2.0',
      method: 'server_event',
      params: {
        eventId: 'evt-1',
        type: 'THOUGHT',
        data: { content: 'Test Thinking' }
      }
    })
    
    expect(logStore.events).toHaveLength(1)
    expect(logStore.events[0].type).toBe('THOUGHT')
    expect(logStore.events[0].data.content).toBe('Test Thinking')
  })

  it('should update fileTreeUpdatedAt on FILE_TREE event', async () => {
    eventBusService.connect()
    await new Promise(resolve => setTimeout(resolve, 20));
    
    const systemStore = useSystemStore()
    const initialTime = systemStore.fileTreeUpdatedAt
    
    MockWebSocket.lastInstance?.simulateMessage({
      jsonrpc: '2.0',
      method: 'server_event',
      params: {
        eventId: 'evt-ft',
        type: 'FILE_TREE',
        data: { name: 'root', type: 'directory', path: '.', children: [] }
      }
    })
    
    expect(systemStore.fileTreeUpdatedAt).toBeGreaterThan(initialTime)
  })

  it('should handle tty events', async () => {
    eventBusService.connect()
    await new Promise(resolve => setTimeout(resolve, 20));
    
    const logStore = useLogStore()
    const toolCallId = 'call-123'
    
    logStore.addEvent({
      eventId: 'start-1',
      type: 'TOOL_START',
      data: { toolCallId, toolName: 'bash', command: 'ls' }
    } as any)
    
    MockWebSocket.lastInstance?.simulateMessage({
      jsonrpc: '2.0',
      method: 'tty_event',
      params: {
        toolCallId,
        text: 'hello\n'
      }
    })
    
    expect(logStore.activeToolCalls[toolCallId]).toContain('hello\n')
  })

  it('should support promise-based send', async () => {
    eventBusService.connect()
    await new Promise(resolve => setTimeout(resolve, 20));
    
    const sendPromise = eventBusService.send('LIST_FILES', {})
    
    const lastId = (eventBusService as any).requestCounter
    MockWebSocket.lastInstance?.simulateMessage({
      jsonrpc: '2.0',
      id: lastId,
      result: { status: 'ok' }
    })
    
    const result = await sendPromise
    expect(result.status).toBe('ok')
  })
})
