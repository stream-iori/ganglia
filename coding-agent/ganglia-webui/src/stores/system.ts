import { create } from 'zustand';
import { useLogStore } from './log';
import type { ToolStartData } from '../types';

export type ConnectionStatus = 'CONNECTED' | 'DISCONNECTED' | 'RECONNECTING';
export type InspectorMode = 'TERMINAL' | 'CODE' | 'DIFF' | 'PLAN';
export type AgentPhase = 'IDLE' | 'PLANNING' | 'EXECUTING' | 'WAITING' | 'REVIEWING';

interface SystemState {
  status: ConnectionStatus;
  sessionId: string;
  sessionHistory: string[];
  workspacePath: string;
  currentPhase: AgentPhase;
  currentView: 'chat' | 'traces';
  isInspectorOpen: boolean;
  inspectorToolCallId: string | null;
  inspectFile: string | null;
  inspectDiff: string | null;
  inspectorMode: InspectorMode;
  terminalSearchQuery: string;
  pendingContextPath: string | null;
  fileTreeUpdatedAt: number;
  activeAskId: string | null;
  mcpCount: number;
  theme: 'light' | 'dark';

  // Actions
  setStatus: (status: ConnectionStatus) => void;
  setCurrentView: (view: 'chat' | 'traces') => void;
  setSessionId: (id: string) => void;
  switchSession: (id: string) => void;
  setWorkspacePath: (path: string) => void;
  setPhase: (phase: AgentPhase) => void;
  toggleInspector: (toolCallId?: string | null, mode?: InspectorMode) => void;
  toggleFileInspector: (path: string) => void;
  toggleDiffInspector: (diffContent: string) => void;
  closeInspector: () => void;
  addContextToPrompt: (path: string) => void;
  clearPendingContext: () => void;
  getModifiedPaths: () => Set<string>;
  setTheme: (theme: 'light' | 'dark') => void;
}

const getInitialSession = () => {
  if (typeof window === 'undefined')
    return { sessionId: 'test', sessionHistory: ['test'], theme: 'dark' as const };
  const savedId = localStorage.getItem('ganglia_session_id');
  const sessionId = savedId || Math.random().toString(36).substring(2, 10);
  if (!savedId) localStorage.setItem('ganglia_session_id', sessionId);

  const savedHistory = localStorage.getItem('ganglia_session_history');
  const sessionHistory = savedHistory ? JSON.parse(savedHistory) : [sessionId];
  if (!sessionHistory.includes(sessionId)) sessionHistory.unshift(sessionId);
  localStorage.setItem('ganglia_session_history', JSON.stringify(sessionHistory));

  const savedTheme = localStorage.getItem('ganglia_theme');
  const theme: 'light' | 'dark' = savedTheme === 'light' ? 'light' : 'dark';

  return { sessionId, sessionHistory, theme };
};

export const useSystemStore = create<SystemState>((set, get) => {
  const initial = getInitialSession();
  return {
    status: 'DISCONNECTED',
    sessionId: initial.sessionId,
    sessionHistory: initial.sessionHistory,
    workspacePath: 'Loading...',
    currentPhase: 'IDLE',
    currentView: 'chat',
    isInspectorOpen: false,
    inspectorToolCallId: null,
    inspectFile: null,
    inspectDiff: null,
    inspectorMode: 'TERMINAL',
    terminalSearchQuery: '',
    pendingContextPath: null,
    fileTreeUpdatedAt: 0,
    activeAskId: null,
    mcpCount: 0,
    theme: initial.theme,

    setStatus: (status) => set({ status }),
    setCurrentView: (view) => set({ currentView: view }),
    setSessionId: (id) => {
      set((state) => {
        localStorage.setItem('ganglia_session_id', id);
        const newHistory = [...state.sessionHistory];
        if (!newHistory.includes(id)) {
          newHistory.unshift(id);
          localStorage.setItem('ganglia_session_history', JSON.stringify(newHistory));
        }
        return { sessionId: id, sessionHistory: newHistory };
      });
    },
    switchSession: (id) => {
      get().setSessionId(id);
      if (typeof window !== 'undefined') window.location.reload();
    },
    setWorkspacePath: (path) => set({ workspacePath: path }),
    setPhase: (phase) => set({ currentPhase: phase }),
    toggleInspector: (toolCallId = null, mode = 'TERMINAL') => {
      set((state) => {
        if (
          state.inspectorToolCallId === toolCallId &&
          state.isInspectorOpen &&
          state.inspectorMode === mode
        ) {
          return { isInspectorOpen: false };
        }
        return {
          inspectorToolCallId: toolCallId,
          inspectorMode: mode,
          isInspectorOpen: true,
        };
      });
    },
    toggleFileInspector: (path) =>
      set({ inspectFile: path, inspectorMode: 'CODE', isInspectorOpen: true }),
    toggleDiffInspector: (diffContent) =>
      set({ inspectDiff: diffContent, inspectorMode: 'DIFF', isInspectorOpen: true }),
    closeInspector: () => set({ isInspectorOpen: false }),
    addContextToPrompt: (path) => set({ pendingContextPath: path }),
    clearPendingContext: () => set({ pendingContextPath: null }),
    setTheme: (theme) => {
      localStorage.setItem('ganglia_theme', theme);
      if (theme === 'light') {
        document.documentElement.classList.remove('dark');
      } else {
        document.documentElement.classList.add('dark');
      }
      set({ theme });
    },
    getModifiedPaths: () => {
      const logStore = useLogStore.getState();
      const paths = new Set<string>();
      logStore.events.forEach((event) => {
        if (event.type === 'TOOL_START') {
          const { toolName, command } = event.data as ToolStartData;
          if (['write_file', 'replace', 'read_file'].includes(toolName)) {
            const path = command?.split(' ')[0]?.replace(/['"]/g, '');
            if (path && path.includes('.')) {
              paths.add(path);
            }
          }
        }
      });
      return paths;
    },
  };
});
