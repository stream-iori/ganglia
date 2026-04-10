import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { useEffect } from 'react';
import { NavBar } from '@/components/NavBar';
import { Dashboard } from '@/pages/Dashboard';
import { PipelineMonitor } from '@/pages/PipelineMonitor';
import { DebateView } from '@/pages/DebateView';
import { MemoryBrowser } from '@/pages/MemoryBrowser';
import { ConfigPanel } from '@/pages/ConfigPanel';
import { TracePage } from '@/pages/TracePage';
import { connect, setupMockMode, disconnect } from '@/services/eventbus';

export default function App() {
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    if (params.get('mock') === 'true') {
      setupMockMode();
    } else {
      connect();
    }
    return () => disconnect();
  }, []);

  return (
    <BrowserRouter>
      <div className="min-h-screen bg-background text-foreground">
        <NavBar />
        <Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="/pipeline" element={<PipelineMonitor />} />
          <Route path="/debate" element={<DebateView />} />
          <Route path="/memory" element={<MemoryBrowser />} />
          <Route path="/config" element={<ConfigPanel />} />
          <Route path="/trace" element={<TracePage />} />
        </Routes>
      </div>
    </BrowserRouter>
  );
}
