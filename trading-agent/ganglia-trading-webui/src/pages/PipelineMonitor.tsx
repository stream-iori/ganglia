import { usePipelineStore } from '@/stores/pipeline';
import { cn } from '@/lib/utils';
import type { PipelinePhase, PipelineStatus } from '@/types';
import { Check, Loader2, Circle, AlertCircle } from 'lucide-react';

const phaseConfig: Record<PipelinePhase, { label: string; description: string }> = {
  PERCEPTION: {
    label: 'Perception',
    description: 'Market data collection and technical analysis',
  },
  DEBATE: {
    label: 'Research Debate',
    description: 'Multi-round bull vs bear debate on fundamentals',
  },
  RISK: {
    label: 'Risk Debate',
    description: 'Aggressive, neutral, and conservative risk assessment',
  },
  SIGNAL: {
    label: 'Signal Extraction',
    description: 'Final signal synthesis from debate outcomes',
  },
};

const phaseOrder: PipelinePhase[] = ['PERCEPTION', 'DEBATE', 'RISK', 'SIGNAL'];

function StatusIcon({ status }: { status: PipelineStatus }) {
  switch (status) {
    case 'COMPLETED':
      return <Check className="h-5 w-5 text-emerald-500" />;
    case 'RUNNING':
      return <Loader2 className="h-5 w-5 text-blue-500 animate-spin" />;
    case 'ERROR':
      return <AlertCircle className="h-5 w-5 text-destructive" />;
    default:
      return <Circle className="h-5 w-5 text-muted-foreground" />;
  }
}

export function PipelineMonitor() {
  const phases = usePipelineStore((s) => s.phases);
  const ticker = usePipelineStore((s) => s.ticker);
  const result = usePipelineStore((s) => s.result);

  return (
    <div className="p-6 max-w-6xl mx-auto space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">Pipeline Monitor</h1>
        {ticker && <span className="text-muted-foreground">Ticker: {ticker}</span>}
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        {phaseOrder.map((phase) => {
          const config = phaseConfig[phase];
          const state = phases[phase];
          return (
            <div
              key={phase}
              className={cn(
                'rounded-lg border p-4',
                state.status === 'RUNNING'
                  ? 'border-blue-500/50 bg-blue-500/5'
                  : state.status === 'COMPLETED'
                    ? 'border-emerald-500/30 bg-emerald-500/5'
                    : state.status === 'ERROR'
                      ? 'border-destructive/30 bg-destructive/5'
                      : 'border-border bg-card'
              )}
            >
              <div className="flex items-center gap-2 mb-2">
                <StatusIcon status={state.status} />
                <h3 className="font-semibold">{config.label}</h3>
              </div>
              <p className="text-sm text-muted-foreground">{config.description}</p>
              <p className="text-xs text-muted-foreground mt-2 uppercase">{state.status}</p>
            </div>
          );
        })}
      </div>

      {result && (
        <div className="space-y-4">
          <h2 className="text-lg font-semibold">Reports</h2>
          {[
            { label: 'Perception Report', content: result.perceptionReport },
            { label: 'Debate Report', content: result.debateReport },
            { label: 'Risk Report', content: result.riskReport },
          ].map(
            ({ label, content }) =>
              content && (
                <div key={label} className="rounded-lg border border-border bg-card p-4">
                  <h3 className="font-medium mb-2">{label}</h3>
                  <p className="text-sm text-muted-foreground whitespace-pre-wrap">{content}</p>
                </div>
              )
          )}
        </div>
      )}
    </div>
  );
}
