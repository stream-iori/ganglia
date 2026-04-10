import { useState } from 'react';
import { usePipelineStore } from '@/stores/pipeline';
import { useSignalStore } from '@/stores/signals';
import { useSystemStore } from '@/stores/system';
import { SignalCard } from '@/components/SignalCard';
import { PipelineStatusStrip } from '@/components/PipelineStatusStrip';
import { SignalHistoryTable } from '@/components/SignalHistoryTable';
import { sendRpc, runMockPipeline } from '@/services/eventbus';
import { Play } from 'lucide-react';

export function Dashboard() {
  const [ticker, setTicker] = useState('AAPL');
  const pipelineStatus = usePipelineStore((s) => s.status);
  const latestSignal = useSignalStore((s) => s.latestSignal);
  const signalHistory = useSignalStore((s) => s.history);
  const connectionStatus = useSystemStore((s) => s.connectionStatus);

  const isMock = new URLSearchParams(window.location.search).get('mock') === 'true';
  const isRunning = pipelineStatus === 'RUNNING';

  const handleRun = () => {
    if (isRunning || !ticker.trim()) return;
    usePipelineStore.getState().startPipeline(ticker);

    if (isMock) {
      runMockPipeline(ticker);
    } else {
      sendRpc('RUN_PIPELINE', { ticker }).catch(console.error);
    }
  };

  return (
    <div className="p-6 max-w-6xl mx-auto space-y-6">
      <h1 className="text-2xl font-bold">Trading Dashboard</h1>

      {/* Run Pipeline */}
      <div className="flex items-center gap-3">
        <input
          type="text"
          value={ticker}
          onChange={(e) => setTicker(e.target.value.toUpperCase())}
          placeholder="Enter ticker (e.g. AAPL)"
          className="px-3 py-2 rounded-md border border-input bg-background text-sm w-40 focus:outline-none focus:ring-2 focus:ring-ring"
        />
        <button
          onClick={handleRun}
          disabled={isRunning || connectionStatus === 'disconnected' && !isMock}
          className="flex items-center gap-2 px-4 py-2 rounded-md bg-primary text-primary-foreground text-sm font-medium hover:bg-primary/90 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          <Play className="h-4 w-4" />
          {isRunning ? 'Running...' : 'Run Pipeline'}
        </button>
      </div>

      {/* Pipeline Status */}
      <PipelineStatusStrip />

      {/* Signal Card */}
      <SignalCard signal={latestSignal} />

      {/* Signal History */}
      <div>
        <h2 className="text-lg font-semibold mb-3">Signal History</h2>
        <SignalHistoryTable signals={signalHistory} />
      </div>
    </div>
  );
}
