<script setup lang="ts">
import { useSystemStore } from '../stores/system'
import { eventBusService } from '../services/eventbus'

const systemStore = useSystemStore()

const handleDiffReview = () => {
  // Try to find the latest diff context to open, or fallback to an empty diff message
  systemStore.toggleDiffInspector("Loading comprehensive diff...")
  // Trigger eventbus to fetch workspace diff if supported by backend
  eventBusService.send('READ_FILE', { path: 'WORKSPACE_DIFF_VIRTUAL_PATH' })
}

const handleMemorySave = () => {
  // Can trigger a local prompt or send a command to the agent to summarize into memory
  eventBusService.send('START', { prompt: 'Please summarize the learnings from this task and save them to MEMORY.md.' })
}
</script>

<template>
  <div class="my-6 bg-slate-900 border border-emerald-500/30 rounded-xl overflow-hidden shadow-[0_0_30px_rgba(16,185,129,0.1)]">
    <div class="bg-emerald-500/10 px-6 py-4 border-b border-emerald-500/20 flex items-center gap-3">
      <span class="text-emerald-500 text-xl">✨</span>
      <div>
        <h3 class="text-sm font-bold text-emerald-500 uppercase tracking-tight">Task Completed</h3>
        <p class="text-xs text-slate-400 mt-0.5">The agent has finished the requested task.</p>
      </div>
    </div>
    
    <div class="p-6">
      <p class="text-slate-300 text-sm mb-6 leading-relaxed">
        What would you like to do next?
      </p>
      
      <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <button 
          @click="handleDiffReview"
          class="flex flex-col items-start p-4 rounded-lg border border-slate-800 bg-slate-950 hover:border-emerald-500/50 hover:bg-slate-900 transition-all text-left group"
        >
          <span class="text-sm font-semibold text-slate-200 group-hover:text-emerald-400 flex items-center gap-2">
            👁️ Review Changes
          </span>
          <span class="text-xs text-slate-500 mt-1">Open the global diff inspector to verify all file modifications.</span>
        </button>

        <button 
          @click="handleMemorySave"
          class="flex flex-col items-start p-4 rounded-lg border border-slate-800 bg-slate-950 hover:border-blue-500/50 hover:bg-slate-900 transition-all text-left group"
        >
          <span class="text-sm font-semibold text-slate-200 group-hover:text-blue-400 flex items-center gap-2">
            🧠 Save to Memory
          </span>
          <span class="text-xs text-slate-500 mt-1">Instruct the agent to document learnings from this session.</span>
        </button>
      </div>
    </div>
  </div>
</template>
