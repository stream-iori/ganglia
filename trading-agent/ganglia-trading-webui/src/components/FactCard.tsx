import { cn } from '@/lib/utils';
import type { FactData } from '@/types';

const roleBadgeColors: Record<string, string> = {
  bull: 'bg-emerald-500/10 text-emerald-500 border-emerald-500/30',
  bear: 'bg-red-500/10 text-red-500 border-red-500/30',
  aggressive: 'bg-orange-500/10 text-orange-500 border-orange-500/30',
  neutral: 'bg-blue-500/10 text-blue-500 border-blue-500/30',
  conservative: 'bg-violet-500/10 text-violet-500 border-violet-500/30',
};

interface FactCardProps {
  fact: FactData;
  onClick?: () => void;
}

export function FactCard({ fact, onClick }: FactCardProps) {
  const isSuperseded = fact.status === 'SUPERSEDED';
  const role = fact.tags?.role || fact.managerId;
  const stance = fact.tags?.stance;

  return (
    <div
      className={cn(
        'rounded-lg border border-border bg-card p-4 cursor-pointer hover:bg-accent/50 transition-colors',
        isSuperseded && 'opacity-60'
      )}
      onClick={onClick}
    >
      <div className="flex items-center gap-2 mb-2">
        <span
          className={cn(
            'px-2 py-0.5 rounded-full text-xs font-medium border',
            roleBadgeColors[role] || 'bg-muted text-muted-foreground border-border'
          )}
        >
          {role}
        </span>
        {stance && (
          <span className="px-2 py-0.5 rounded-full text-xs bg-muted text-muted-foreground">
            {stance}
          </span>
        )}
        <span className="text-xs text-muted-foreground ml-auto">Cycle {fact.cycleNumber}</span>
      </div>
      <p className={cn('text-sm', isSuperseded && 'line-through text-muted-foreground')}>
        {fact.summary}
      </p>
    </div>
  );
}
