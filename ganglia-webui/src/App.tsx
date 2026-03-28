import React, { useEffect } from 'react';
import { eventBusService } from './services/eventbus';
import { useSystemStore } from './stores/system';
import Sidebar from './components/Sidebar';
import MainStream from './components/MainStream';
import Inspector from './components/Inspector';
import TraceStudio from './components/TraceStudio';

const App: React.FC = () => {
  const theme = useSystemStore((state) => state.theme);
  const currentView = useSystemStore((state) => state.currentView);

  useEffect(() => {
    if (theme === 'light') {
      document.documentElement.classList.remove('dark');
    } else {
      document.documentElement.classList.add('dark');
    }
  }, [theme]);

  useEffect(() => {
    eventBusService.connect();
  }, []);

  if (currentView === 'traces') {
    return (
      <div className="flex h-screen w-screen overflow-hidden bg-slate-950 font-sans antialiased text-slate-200">
        <Sidebar />
        <TraceStudio />
      </div>
    );
  }

  return (
    <div className="flex h-screen w-screen overflow-hidden bg-slate-950 font-sans antialiased text-slate-200">
      <Sidebar />
      <MainStream />
      <Inspector />
    </div>
  );
};

export default App;
