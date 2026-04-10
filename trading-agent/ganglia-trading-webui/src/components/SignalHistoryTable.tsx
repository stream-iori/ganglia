import { cn } from '@/lib/utils';
import type { SignalHistoryEntry } from '@/types';

const signalColors: Record<string, string> = {
  BUY: 'text-emerald-500',
  OVERWEIGHT: 'text-emerald-400',
  HOLD: 'text-amber-500',
  UNDERWEIGHT: 'text-orange-500',
  SELL: 'text-red-500',
};

interface SignalHistoryTableProps {
  signals: SignalHistoryEntry[];
}

export function SignalHistoryTable({ signals }: SignalHistoryTableProps) {
  if (signals.length === 0) {
    return (
      <div className="text-center py-8 text-muted-foreground text-sm">
        No signals yet. Run a pipeline to generate signals.
      </div>
    );
  }

  return (
    <div className="rounded-lg border border-border overflow-hidden">
      <table className="w-full text-sm">
        <thead className="bg-muted/50">
          <tr>
            <th className="text-left px-4 py-2 font-medium text-muted-foreground">Ticker</th>
            <th className="text-left px-4 py-2 font-medium text-muted-foreground">Signal</th>
            <th className="text-left px-4 py-2 font-medium text-muted-foreground">Confidence</th>
            <th className="text-left px-4 py-2 font-medium text-muted-foreground">Rationale</th>
            <th className="text-left px-4 py-2 font-medium text-muted-foreground">Time</th>
          </tr>
        </thead>
        <tbody>
          {[...signals].reverse().map((s, i) => (
            <tr key={i} className="border-t border-border">
              <td className="px-4 py-2 font-medium">{s.ticker}</td>
              <td className={cn('px-4 py-2 font-semibold', signalColors[s.signal])}>
                {s.signal}
              </td>
              <td className="px-4 py-2">{Math.round(s.confidence * 100)}%</td>
              <td className="px-4 py-2 text-muted-foreground max-w-xs truncate">{s.rationale}</td>
              <td className="px-4 py-2 text-muted-foreground">
                {new Date(s.timestamp).toLocaleTimeString()}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
