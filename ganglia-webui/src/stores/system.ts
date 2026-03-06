import { defineStore } from 'pinia'

export type ConnectionStatus = 'CONNECTED' | 'DISCONNECTED' | 'RECONNECTING'
export type InspectorMode = 'TERMINAL' | 'CODE'

export const useSystemStore = defineStore('system', {
  state: () => {
    const savedId = localStorage.getItem('ganglia_session_id')
    const sessionId = savedId || Math.random().toString(36).substring(2, 10)
    if (!savedId) localStorage.setItem('ganglia_session_id', sessionId)
    
    return {
      status: 'DISCONNECTED' as ConnectionStatus,
      sessionId,
      isInspectorOpen: false,
      inspectorToolCallId: null as string | null,
      inspectFile: null as string | null,
      inspectorMode: 'TERMINAL' as InspectorMode,
    }
  },
  actions: {
    setStatus(status: ConnectionStatus) {
      this.status = status
    },
    setSessionId(id: string) {
      this.sessionId = id
      localStorage.setItem('ganglia_session_id', id)
    },
    toggleInspector(toolCallId: string | null = null, mode: InspectorMode = 'TERMINAL') {
      if (this.inspectorToolCallId === toolCallId && this.isInspectorOpen && this.inspectorMode === mode) {
        this.isInspectorOpen = false
      } else {
        this.inspectorToolCallId = toolCallId
        this.inspectorMode = mode
        this.isInspectorOpen = true
      }
    },
    toggleFileInspector(path: string) {
      this.inspectFile = path
      this.inspectorMode = 'CODE'
      this.isInspectorOpen = true
    },
    closeInspector() {
      this.isInspectorOpen = false
    }
  }
})
