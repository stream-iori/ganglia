import React from 'react';
import { usePlanStore } from '../stores/plan';
import { cn } from '../lib/utils';

const PlanSidebar: React.FC = () => {
  const { plan } = usePlanStore();

  if (!plan || !plan.items || plan.items.length === 0) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center p-8 text-center bg-slate-950">
        <div className="text-4xl mb-4 opacity-20 text-blue-500">📋</div>
        <h4 className="text-sm font-medium text-slate-400">No active plan</h4>
        <p className="text-[10px] text-slate-600 mt-2 max-w-xs leading-relaxed">
          When the agent creates a plan using the <code>todo_add</code> tool, it will appear here.
        </p>
      </div>
    );
  }

  return (
    <div className="flex-1 overflow-y-auto p-6 bg-slate-950 scrollbar-thin scrollbar-thumb-slate-800">
      <div className="space-y-4">
        {plan.items.map((item, index) => (
          <div
            key={item.id}
            className={cn(
              'p-4 rounded-xl border transition-all duration-300 relative overflow-hidden group',
              item.status === 'DONE'
                ? 'bg-emerald-950/10 border-emerald-900/30 opacity-70'
                : item.status === 'IN_PROGRESS'
                  ? 'bg-blue-900/10 border-blue-500/30 shadow-[0_0_20px_rgba(59,130,246,0.05)]'
                  : 'bg-slate-900/50 border-slate-800',
            )}
          >
            {/* Status Indicator */}
            <div className="flex items-start gap-3 relative z-10">
              <div
                className={cn(
                  'mt-0.5 w-5 h-5 rounded-full flex items-center justify-center shrink-0 border',
                  item.status === 'DONE'
                    ? 'bg-emerald-500/20 border-emerald-500/50 text-emerald-400'
                    : item.status === 'IN_PROGRESS'
                      ? 'bg-blue-500/20 border-blue-500/50 text-blue-400 animate-pulse'
                      : 'bg-slate-800 border-slate-700 text-slate-500',
                )}
              >
                {item.status === 'DONE' ? (
                  <svg
                    xmlns="http://www.w3.org/2000/svg"
                    width="12"
                    height="12"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="3"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  >
                    <polyline points="20 6 9 17 4 12"></polyline>
                  </svg>
                ) : (
                  <span className="text-[10px] font-bold">{index + 1}</span>
                )}
              </div>

              <div className="flex-1 min-w-0">
                <div className="flex items-center justify-between gap-2 mb-1">
                  <span
                    className={cn(
                      'text-[10px] font-bold uppercase tracking-widest',
                      item.status === 'DONE'
                        ? 'text-emerald-500/70'
                        : item.status === 'IN_PROGRESS'
                          ? 'text-blue-400'
                          : 'text-slate-500',
                    )}
                  >
                    {item.status === 'DONE'
                      ? 'Completed'
                      : item.status === 'IN_PROGRESS'
                        ? 'In Progress'
                        : 'Pending'}
                  </span>
                  <span className="text-[10px] font-mono text-slate-600">ID: {item.id}</span>
                </div>

                <h4
                  className={cn(
                    'text-sm font-medium leading-relaxed',
                    item.status === 'DONE' ? 'text-slate-400 line-through' : 'text-slate-200',
                  )}
                >
                  {item.description}
                </h4>

                {item.result && (
                  <div className="mt-3 p-3 bg-black/40 rounded-lg border border-white/5 text-[11px] text-slate-400 font-mono leading-normal">
                    <div className="text-[9px] uppercase tracking-tighter text-slate-600 mb-1 font-bold">
                      Execution Summary
                    </div>
                    {item.result}
                  </div>
                )}
              </div>
            </div>

            {/* Background progress bar for IN_PROGRESS */}
            {item.status === 'IN_PROGRESS' && (
              <div className="absolute bottom-0 left-0 h-0.5 bg-blue-500/50 w-full overflow-hidden">
                <div className="h-full bg-blue-400 animate-[shimmer_2s_infinite] w-1/3"></div>
              </div>
            )}
          </div>
        ))}
      </div>

      <style
        dangerouslySetInnerHTML={{
          __html: `
        @keyframes shimmer {
          0% { transform: translateX(-100%); }
          100% { transform: translateX(300%); }
        }
      `,
        }}
      />
    </div>
  );
};

export default PlanSidebar;
