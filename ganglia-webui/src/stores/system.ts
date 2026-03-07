import { defineStore } from 'pinia'
import { useLogStore } from './log'

export type ConnectionStatus = 'CONNECTED' | 'DISCONNECTED' | 'RECONNECTING'
export type InspectorMode = 'TERMINAL' | 'CODE' | 'DIFF'
export type AgentPhase = 'IDLE' | 'PLANNING' | 'EXECUTING' | 'WAITING' | 'REVIEWING'

export const useSystemStore = defineStore('system', {
  state: () => {
    const savedId = localStorage.getItem('ganglia_session_id')
    const sessionId = savedId || Math.random().toString(36).substring(2, 10)
    if (!savedId) localStorage.setItem('ganglia_session_id', sessionId)
    
    const savedHistory = localStorage.getItem('ganglia_session_history')
    const sessionHistory = savedHistory ? JSON.parse(savedHistory) : [sessionId]
    if (!sessionHistory.includes(sessionId)) sessionHistory.unshift(sessionId)
    localStorage.setItem('ganglia_session_history', JSON.stringify(sessionHistory))

    return {
      status: 'DISCONNECTED' as ConnectionStatus,
      sessionId,
      sessionHistory: sessionHistory as string[],
      workspacePath: 'Loading...',
      currentPhase: 'IDLE' as AgentPhase,
      isInspectorOpen: false,
      inspectorToolCallId: null as string | null,
      inspectFile: null as string | null,
      inspectDiff: null as string | null,
      inspectorMode: 'TERMINAL' as InspectorMode,
      terminalSearchQuery: '',
      pendingContextPath: null as string | null,
      fileTreeUpdatedAt: 0,
    }
  },
  getters: {
    modifiedPaths: (_state) => {
      const logStore = useLogStore()
      const paths = new Set<string>()
      
      logStore.events.forEach(event => {
        if (event.type === 'TOOL_START') {
          const { toolName, command } = event.data
          if (['write_file', 'replace', 'read_file'].includes(toolName)) {
            // Heuristic: the command for these tools often starts with the path or is just the path
            // This is a bit brittle but works for many cases in the current agent implementation
            const path = command.split(' ')[0].replace(/['"]/g, '')
            if (path && path.includes('.')) {
              paths.add(path)
            }
          }
        }
      })
      return paths
    }
  },
  actions: {
    setStatus(status: ConnectionStatus) {
      this.status = status
    },
    setSessionId(id: string) {
      this.sessionId = id
      localStorage.setItem('ganglia_session_id', id)
      if (!this.sessionHistory.includes(id)) {
        this.sessionHistory.unshift(id)
        localStorage.setItem('ganglia_session_history', JSON.stringify(this.sessionHistory))
      }
    },
    switchSession(id: string) {
      this.setSessionId(id)
      window.location.reload()
    },
    setWorkspacePath(path: string) {
      this.workspacePath = path
    },
    setPhase(phase: AgentPhase) {
      this.currentPhase = phase
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
    toggleDiffInspector(diffContent: string) {
      this.inspectDiff = diffContent
      this.inspectorMode = 'DIFF'
      this.isInspectorOpen = true
    },
    closeInspector() {
      this.isInspectorOpen = false
    },
    addContextToPrompt(path: string) {
      this.pendingContextPath = path
    },
    clearPendingContext() {
      this.pendingContextPath = null
    }
  }
})
