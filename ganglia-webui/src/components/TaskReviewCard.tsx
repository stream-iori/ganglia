import React from 'react'
import { useSystemStore } from '../stores/system'
import { eventBusService } from '../services/eventbus'

const TaskReviewCard: React.FC = () => {
  const systemStore = useSystemStore()

  const handleDiffReview = () => {
    systemStore.toggleDiffInspector("Loading comprehensive diff...")
    eventBusService.send('READ_FILE', { path: 'WORKSPACE_DIFF_VIRTUAL_PATH' })
  }

  const handleMemorySave = () => {
    eventBusService.send('START', { prompt: 'Please summarize the learnings from this task and save them to .ganglia/memory/MEMORY.md.' })
  }

  return (
    <div className="my-6 bg-slate-900 border border-emerald-500/30 rounded-xl overflow-hidden shadow-[0_0_30px_rgba(16,185,129,0.1)]">
      <div className="bg-emerald-500/10 px-6 py-4 border-b border-emerald-500/20 flex items-center gap-3">
        <span className="text-emerald-500 text-xl">✨</span>
        <div>
          <h3 className="text-sm font-bold text-emerald-500 uppercase tracking-tight">Task Completed</h3>
          <p className="text-xs text-slate-400 mt-0.5">The agent has finished the requested task.</p>
        </div>
      </div>

      <div className="p-6">
        <p className="text-slate-300 text-sm mb-6 leading-relaxed">What would you like to do next?</p>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <button
            onClick={handleDiffReview}
            className="flex flex-col items-start p-4 rounded-lg border border-slate-800 bg-slate-950 hover:border-emerald-500/50 hover:bg-slate-900 transition-all text-left group"
          >
            <span className="text-sm font-semibold text-slate-200 group-hover:text-emerald-400 flex items-center gap-2">
              👁️ Review Changes
            </span>
            <span className="text-xs text-slate-500 mt-1">Open the global diff inspector to verify all file modifications.</span>
          </button>

          <button
            onClick={handleMemorySave}
            className="flex flex-col items-start p-4 rounded-lg border border-slate-800 bg-slate-950 hover:border-blue-500/50 hover:bg-slate-900 transition-all text-left group"
          >
            <span className="text-sm font-semibold text-slate-200 group-hover:text-blue-400 flex items-center gap-2">
              🧠 Save to Memory
            </span>
            <span className="text-xs text-slate-500 mt-1">Instruct the agent to document learnings from this session.</span>
          </button>
        </div>
      </div>
    </div>
  )
}

export default TaskReviewCard
