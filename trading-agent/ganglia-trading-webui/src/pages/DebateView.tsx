import { useState } from 'react';
import { useDebateStore } from '@/stores/debate';
import { FactCard } from '@/components/FactCard';
import { DebateTimeline } from '@/components/DebateTimeline';
import { cn } from '@/lib/utils';
import type { DebateType } from '@/types';

export function DebateView() {
  const [activeTab, setActiveTab] = useState<DebateType>('RESEARCH');
  const researchDebate = useDebateStore((s) => s.researchDebate);
  const riskDebate = useDebateStore((s) => s.riskDebate);

  const currentDebate = activeTab === 'RESEARCH' ? researchDebate : riskDebate;

  const cycles = Array.from({ length: currentDebate.maxCycles || currentDebate.currentCycle }, (_, i) => ({
    cycleNumber: i + 1,
    status: (i + 1 < currentDebate.currentCycle
      ? 'completed'
      : i + 1 === currentDebate.currentCycle
        ? currentDebate.status === 'CONVERGED' || currentDebate.status === 'STALLED'
          ? 'completed'
          : 'running'
        : 'pending') as 'completed' | 'running' | 'pending',
    decisionType: i + 1 === currentDebate.currentCycle ? currentDebate.decisionType : undefined,
  }));

  // Split facts by role for side-by-side view
  const bullFacts = currentDebate.facts.filter(
    (f) => f.tags?.role === 'bull' || f.tags?.role === 'aggressive'
  );
  const bearFacts = currentDebate.facts.filter(
    (f) => f.tags?.role === 'bear' || f.tags?.role === 'conservative'
  );
  const neutralFacts = currentDebate.facts.filter(
    (f) => f.tags?.role === 'neutral' || (!f.tags?.role)
  );

  return (
    <div className="p-6 max-w-6xl mx-auto space-y-6">
      <h1 className="text-2xl font-bold">Debate View</h1>

      {/* Tabs */}
      <div className="flex gap-1 border-b border-border">
        {(['RESEARCH', 'RISK'] as DebateType[]).map((tab) => (
          <button
            key={tab}
            onClick={() => setActiveTab(tab)}
            className={cn(
              'px-4 py-2 text-sm font-medium border-b-2 -mb-px transition-colors',
              activeTab === tab
                ? 'border-primary text-foreground'
                : 'border-transparent text-muted-foreground hover:text-foreground'
            )}
          >
            {tab === 'RESEARCH' ? 'Research Debate' : 'Risk Debate'}
          </button>
        ))}
      </div>

      {/* Status */}
      <div className="flex items-center gap-4">
        <span className="text-sm text-muted-foreground">
          Status:{' '}
          <span
            className={cn(
              'font-medium',
              currentDebate.status === 'CONVERGED'
                ? 'text-emerald-500'
                : currentDebate.status === 'STALLED'
                  ? 'text-amber-500'
                  : currentDebate.status === 'RUNNING'
                    ? 'text-blue-500'
                    : 'text-muted-foreground'
            )}
          >
            {currentDebate.status}
          </span>
        </span>
        {currentDebate.currentCycle > 0 && (
          <span className="text-sm text-muted-foreground">
            Cycle {currentDebate.currentCycle} / {currentDebate.maxCycles}
          </span>
        )}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
        {/* Timeline */}
        <div className="lg:col-span-1">
          <h3 className="font-medium mb-3 text-sm text-muted-foreground">Cycle Timeline</h3>
          {cycles.length > 0 ? (
            <DebateTimeline cycles={cycles} />
          ) : (
            <p className="text-sm text-muted-foreground">No cycles yet</p>
          )}
        </div>

        {/* Facts - Side by Side */}
        <div className="lg:col-span-3 grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <h3 className="font-medium mb-3 text-sm text-emerald-500">
              {activeTab === 'RESEARCH' ? 'Bull' : 'Aggressive'}
            </h3>
            <div className="space-y-2">
              {bullFacts.map((f) => (
                <FactCard key={f.id} fact={f} />
              ))}
              {bullFacts.length === 0 && (
                <p className="text-sm text-muted-foreground">No facts yet</p>
              )}
            </div>
          </div>
          <div>
            <h3 className="font-medium mb-3 text-sm text-red-500">
              {activeTab === 'RESEARCH' ? 'Bear' : 'Conservative'}
            </h3>
            <div className="space-y-2">
              {bearFacts.map((f) => (
                <FactCard key={f.id} fact={f} />
              ))}
              {bearFacts.length === 0 && (
                <p className="text-sm text-muted-foreground">No facts yet</p>
              )}
            </div>
          </div>
          {neutralFacts.length > 0 && (
            <div className="md:col-span-2">
              <h3 className="font-medium mb-3 text-sm text-blue-500">Neutral</h3>
              <div className="space-y-2">
                {neutralFacts.map((f) => (
                  <FactCard key={f.id} fact={f} />
                ))}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
