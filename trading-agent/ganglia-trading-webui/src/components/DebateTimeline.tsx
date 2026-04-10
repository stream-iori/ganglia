import { cn } from '@/lib/utils';

interface CycleNode {
  cycleNumber: number;
  status: 'completed' | 'running' | 'pending';
  decisionType?: string;
}

interface DebateTimelineProps {
  cycles: CycleNode[];
  onCycleClick?: (cycleNumber: number) => void;
}

const decisionBadge: Record<string, { label: string; color: string }> = {
  CONVERGED: { label: 'Converged', color: 'text-emerald-500' },
  STALLED: { label: 'Stalled', color: 'text-amber-500' },
  BUDGET_EXCEEDED: { label: 'Budget', color: 'text-red-500' },
};

export function DebateTimeline({ cycles, onCycleClick }: DebateTimelineProps) {
  return (
    <div className="flex flex-col gap-1">
      {cycles.map((cycle) => (
        <div
          key={cycle.cycleNumber}
          className={cn(
            'flex items-center gap-3 px-3 py-2 rounded-md cursor-pointer hover:bg-accent/50 transition-colors',
            cycle.status === 'running' && 'bg-blue-500/5 border border-blue-500/30'
          )}
          onClick={() => onCycleClick?.(cycle.cycleNumber)}
        >
          <div
            className={cn(
              'w-3 h-3 rounded-full',
              cycle.status === 'completed'
                ? 'bg-emerald-500'
                : cycle.status === 'running'
                  ? 'bg-blue-500 animate-pulse-dot'
                  : 'bg-muted-foreground/30'
            )}
          />
          <span className="text-sm font-medium">Cycle {cycle.cycleNumber}</span>
          {cycle.decisionType && decisionBadge[cycle.decisionType] && (
            <span
              className={cn('text-xs font-medium ml-auto', decisionBadge[cycle.decisionType].color)}
            >
              {decisionBadge[cycle.decisionType].label}
            </span>
          )}
        </div>
      ))}
    </div>
  );
}
