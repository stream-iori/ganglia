import { cn } from '@/lib/utils';
import type { SignalHistoryEntry } from '@/types';

const signalConfig: Record<string, { color: string; bg: string; label: string }> = {
  BUY: { color: 'text-emerald-500', bg: 'bg-emerald-500/10', label: 'BUY' },
  OVERWEIGHT: { color: 'text-emerald-400', bg: 'bg-emerald-400/10', label: 'OVERWEIGHT' },
  HOLD: { color: 'text-amber-500', bg: 'bg-amber-500/10', label: 'HOLD' },
  UNDERWEIGHT: { color: 'text-orange-500', bg: 'bg-orange-500/10', label: 'UNDERWEIGHT' },
  SELL: { color: 'text-red-500', bg: 'bg-red-500/10', label: 'SELL' },
};

interface SignalCardProps {
  signal: SignalHistoryEntry | null;
}

export function SignalCard({ signal }: SignalCardProps) {
  if (!signal) {
    return (
      <div className="rounded-lg border border-border bg-card p-6 text-center">
        <p className="text-2xl font-bold text-muted-foreground">No signal</p>
        <p className="text-sm text-muted-foreground mt-1">Run a pipeline to generate a signal</p>
      </div>
    );
  }

  const config = signalConfig[signal.signal] || signalConfig.HOLD;

  return (
    <div className={cn('rounded-lg border border-border bg-card p-6', config.bg)}>
      <div className="flex items-center justify-between mb-3">
        <span className="text-sm font-medium text-muted-foreground">{signal.ticker}</span>
        <span className="text-xs text-muted-foreground">
          {new Date(signal.timestamp).toLocaleTimeString()}
        </span>
      </div>
      <p className={cn('text-3xl font-bold', config.color)}>{config.label}</p>
      <div className="mt-3">
        <div className="flex items-center gap-2 mb-1">
          <span className="text-sm text-muted-foreground">Confidence</span>
          <span className="text-sm font-medium">{Math.round(signal.confidence * 100)}%</span>
        </div>
        <div className="w-full bg-muted rounded-full h-2">
          <div
            className={cn('h-2 rounded-full', config.color.replace('text-', 'bg-'))}
            style={{ width: `${signal.confidence * 100}%` }}
          />
        </div>
      </div>
      {signal.rationale && (
        <p className="mt-3 text-sm text-muted-foreground">{signal.rationale}</p>
      )}
    </div>
  );
}
