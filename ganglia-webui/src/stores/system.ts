import { create } from 'zustand'
import { useLogStore } from './log'

export type ConnectionStatus = 'CONNECTED' | 'DISCONNECTED' | 'RECONNECTING'
export type InspectorMode = 'TERMINAL' | 'CODE' | 'DIFF'
export type AgentPhase = 'IDLE' | 'PLANNING' | 'EXECUTING' | 'WAITING' | 'REVIEWING'

interface SystemState {
  status: ConnectionStatus
  sessionId: string
  sessionHistory: string[]
  workspacePath: string
  currentPhase: AgentPhase
  isInspectorOpen: boolean
  inspectorToolCallId: string | null
  inspectFile: string | null
  inspectDiff: string | null
  inspectorMode: InspectorMode
  terminalSearchQuery: string
  pendingContextPath: string | null
  fileTreeUpdatedAt: number
  activeAskId: string | null

  // Actions
  setStatus: (status: ConnectionStatus) => void
  setSessionId: (id: string) => void
  switchSession: (id: string) => void
  setWorkspacePath: (path: string) => void
  setPhase: (phase: AgentPhase) => void
  toggleInspector: (toolCallId?: string | null, mode?: InspectorMode) => void
  toggleFileInspector: (path: string) => void
  toggleDiffInspector: (diffContent: string) => void
  closeInspector: () => void
  addContextToPrompt: (path: string) => void
  clearPendingContext: () => void
  getModifiedPaths: () => Set<string>
}

const getInitialSession = () => {
  if (typeof window === 'undefined') return { sessionId: 'test', sessionHistory: ['test'] }
  const savedId = localStorage.getItem('ganglia_session_id')
  const sessionId = savedId || Math.random().toString(36).substring(2, 10)
  if (!savedId) localStorage.setItem('ganglia_session_id', sessionId)
  
  const savedHistory = localStorage.getItem('ganglia_session_history')
  const sessionHistory = savedHistory ? JSON.parse(savedHistory) : [sessionId]
  if (!sessionHistory.includes(sessionId)) sessionHistory.unshift(sessionId)
  localStorage.setItem('ganglia_session_history', JSON.stringify(sessionHistory))

  return { sessionId, sessionHistory }
}

export const useSystemStore = create<SystemState>((set, get) => {
  const initial = getInitialSession()
  return {
    status: 'DISCONNECTED',
    sessionId: initial.sessionId,
    sessionHistory: initial.sessionHistory,
    workspacePath: 'Loading...',
    currentPhase: 'IDLE',
    isInspectorOpen: false,
    inspectorToolCallId: null,
    inspectFile: null,
    inspectDiff: null,
    inspectorMode: 'TERMINAL',
    terminalSearchQuery: '',
    pendingContextPath: null,
    fileTreeUpdatedAt: 0,
    activeAskId: null,

    setStatus: (status) => set({ status }),
    setSessionId: (id) => {
      set((state) => {
        localStorage.setItem('ganglia_session_id', id)
        const newHistory = [...state.sessionHistory]
        if (!newHistory.includes(id)) {
          newHistory.unshift(id)
          localStorage.setItem('ganglia_session_history', JSON.stringify(newHistory))
        }
        return { sessionId: id, sessionHistory: newHistory }
      })
    },
    switchSession: (id) => {
      get().setSessionId(id)
      if (typeof window !== 'undefined') window.location.reload()
    },
    setWorkspacePath: (path) => set({ workspacePath: path }),
    setPhase: (phase) => set({ currentPhase: phase }),
    toggleInspector: (toolCallId = null, mode = 'TERMINAL') => {
      set((state) => {
        if (state.inspectorToolCallId === toolCallId && state.isInspectorOpen && state.inspectorMode === mode) {
          return { isInspectorOpen: false }
        }
        return {
          inspectorToolCallId: toolCallId,
          inspectorMode: mode,
          isInspectorOpen: true
        }
      })
    },
    toggleFileInspector: (path) => set({ inspectFile: path, inspectorMode: 'CODE', isInspectorOpen: true }),
    toggleDiffInspector: (diffContent) => set({ inspectDiff: diffContent, inspectorMode: 'DIFF', isInspectorOpen: true }),
    closeInspector: () => set({ isInspectorOpen: false }),
    addContextToPrompt: (path) => set({ pendingContextPath: path }),
    clearPendingContext: () => set({ pendingContextPath: null }),
    getModifiedPaths: () => {
      const logStore = useLogStore.getState()
      const paths = new Set<string>()
      logStore.events.forEach(event => {
        if (event.type === 'TOOL_START') {
          const { toolName, command } = event.data as any
          if (['write_file', 'replace', 'read_file'].includes(toolName)) {
            const path = command?.split(' ')[0]?.replace(/['"]/g, '')
            if (path && path.includes('.')) {
              paths.add(path)
            }
          }
        }
      })
      return paths
    }
  }
})
