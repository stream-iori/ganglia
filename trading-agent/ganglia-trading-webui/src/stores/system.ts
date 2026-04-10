import { create } from 'zustand';

type ConnectionStatus = 'disconnected' | 'connecting' | 'connected';

interface SystemState {
  connectionStatus: ConnectionStatus;
  sessionId: string;
  theme: 'dark' | 'light';

  setConnectionStatus: (status: ConnectionStatus) => void;
  setSessionId: (id: string) => void;
  toggleTheme: () => void;
}

export const useSystemStore = create<SystemState>((set) => ({
  connectionStatus: 'disconnected',
  sessionId: `session-${Date.now()}`,
  theme: (localStorage.getItem('trading_theme') as 'dark' | 'light') || 'dark',

  setConnectionStatus: (status) => set({ connectionStatus: status }),
  setSessionId: (id) => set({ sessionId: id }),
  toggleTheme: () =>
    set((state) => {
      const next = state.theme === 'dark' ? 'light' : 'dark';
      localStorage.setItem('trading_theme', next);
      if (next === 'dark') {
        document.documentElement.classList.add('dark');
      } else {
        document.documentElement.classList.remove('dark');
      }
      return { theme: next };
    }),
}));
