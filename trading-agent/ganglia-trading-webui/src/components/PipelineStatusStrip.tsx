import { cn } from '@/lib/utils';
import { usePipelineStore } from '@/stores/pipeline';
import type { PipelinePhase, PipelineStatus } from '@/types';
import { Check, Loader2, Circle, AlertCircle } from 'lucide-react';

const phaseLabels: Record<PipelinePhase, string> = {
  PERCEPTION: 'Perception',
  DEBATE: 'Research Debate',
  RISK: 'Risk Debate',
  SIGNAL: 'Signal Extraction',
};

const phaseOrder: PipelinePhase[] = ['PERCEPTION', 'DEBATE', 'RISK', 'SIGNAL'];

function PhaseIcon({ status }: { status: PipelineStatus }) {
  switch (status) {
    case 'COMPLETED':
      return <Check className="h-4 w-4 text-emerald-500" />;
    case 'RUNNING':
      return <Loader2 className="h-4 w-4 text-blue-500 animate-spin" />;
    case 'ERROR':
      return <AlertCircle className="h-4 w-4 text-destructive" />;
    default:
      return <Circle className="h-4 w-4 text-muted-foreground" />;
  }
}

export function PipelineStatusStrip() {
  const phases = usePipelineStore((s) => s.phases);

  return (
    <div className="flex items-center gap-2">
      {phaseOrder.map((phase, i) => (
        <div key={phase} className="flex items-center gap-2">
          <div
            className={cn(
              'flex items-center gap-2 px-3 py-2 rounded-md border',
              phases[phase].status === 'RUNNING'
                ? 'border-blue-500/50 bg-blue-500/5'
                : phases[phase].status === 'COMPLETED'
                  ? 'border-emerald-500/30 bg-emerald-500/5'
                  : 'border-border bg-card'
            )}
          >
            <PhaseIcon status={phases[phase].status} />
            <span className="text-sm font-medium">{phaseLabels[phase]}</span>
          </div>
          {i < phaseOrder.length - 1 && (
            <div className="w-6 h-px bg-border" />
          )}
        </div>
      ))}
    </div>
  );
}
