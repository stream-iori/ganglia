// @ts-ignore
import EventBus from '@vertx/eventbus-bridge-client.js'
import 'sockjs-client'
import { useSystemStore } from '../stores/system'
import { useLogStore } from '../stores/log'
import type { ServerEvent, ClientAction, TtyData } from '../types'

class EventBusService {
  private eb: any
  private url: string = '/eventbus'
  private reconnectTimer: any
  private retryCount: number = 0
  private maxRetryCount: number = 10
  private isManualClosed: boolean = false

  constructor() {
    if (import.meta.env.DEV) {
      this.url = 'http://localhost:8080/eventbus'
    }
  }

  connect() {
    const systemStore = useSystemStore()
    console.log(`Connecting to EventBus at ${this.url}...`)
    
    this.eb = new EventBus(this.url, {
      vertxbus_reconnect_attempts_max: 0,
      sockjsOptions: {
        transports: ['websocket', 'xhr-streaming', 'xhr-polling']
      }
    })

    this.eb.onopen = () => {
      console.log('EventBus connected')
      systemStore.setStatus('CONNECTED')
      this.retryCount = 0
      
      const sessionTopic = `ganglia.ui.stream.${systemStore.sessionId}`
      
      // Request history sync
      this.eb.send('ganglia.ui.req', { action: 'SYNC', sessionId: systemStore.sessionId }, (err: any, reply: any) => {
        if (!err && reply && reply.body && reply.body.history) {
          const logStore = useLogStore()
          logStore.clear()
          const history = reply.body.history as ServerEvent[]
          history.forEach((event: ServerEvent) => logStore.addEvent(event))
          
          // If no file tree in history, request a fresh one
          if (!logStore.fileTree) {
            this.send('LIST_FILES', {})
          }
        } else {
          // If sync fails or no history, at least get the file tree
          this.send('LIST_FILES', {})
        }
      })

      this.eb.registerHandler(sessionTopic, (error: any, message: { body: ServerEvent }) => {
        if (error) {
          console.error('Error in session stream handler', error)
          return
        }
        this.handleServerEvent(message.body)
      })

      const ttyTopic = `${sessionTopic}.tty`
      this.eb.registerHandler(ttyTopic, (error: any, message: { body: TtyData }) => {
        if (error) return
        this.handleTtyEvent(message.body)
      })
    }

    this.eb.onclose = () => {
      console.log('EventBus disconnected')
      systemStore.setStatus('DISCONNECTED')
      if (!this.isManualClosed) {
        this.reconnect()
      }
    }
  }

  private reconnect() {
    if (this.retryCount >= this.maxRetryCount) return

    const systemStore = useSystemStore()
    systemStore.setStatus('RECONNECTING')
    
    this.retryCount++
    const delay = Math.min(1000 * Math.pow(2, this.retryCount), 30000)
    
    clearTimeout(this.reconnectTimer)
    this.reconnectTimer = setTimeout(() => {
      this.connect()
    }, delay)
  }

  send(action: ClientAction, payload: any) {
    const systemStore = useSystemStore()
    if (systemStore.status !== 'CONNECTED') return

    const request = {
      action,
      sessionId: systemStore.sessionId,
      payload
    }

    this.eb.send('ganglia.ui.req', request)
  }

  private handleServerEvent(event: ServerEvent) {
    console.debug('[EventBus] Received event:', event.type, event);
    const logStore = useLogStore()
    logStore.addEvent(event)
  }

  private handleTtyEvent(event: TtyData) {
    const logStore = useLogStore()
    if (event.toolCallId && event.text) {
      logStore.appendTty(event.toolCallId, event.text)
    }
  }

  close() {
    this.isManualClosed = true
    if (this.eb) {
      this.eb.close()
    }
  }
}

export const eventBusService = new EventBusService()
