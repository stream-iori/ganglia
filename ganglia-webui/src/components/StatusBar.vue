<script setup lang="ts">
import { computed, watch, ref } from 'vue'
import { useSystemStore, type AgentPhase } from '../stores/system'
import { useLogStore } from '../stores/log'
import { eventBusService } from '../services/eventbus'

const systemStore = useSystemStore()
const logStore = useLogStore()

const showFileSyncTip = ref(false)
let syncTipTimer: any = null

const isAgentBusy = computed(() => {
  if (logStore.streamingMessage) return true;
  const toolStarts = logStore.events.filter(e => e.type === 'TOOL_START');
  const toolResults = logStore.events.filter(e => e.type === 'TOOL_RESULT');
  return toolStarts.length > toolResults.length;
})

// Heuristic phase detection based on events
watch(() => logStore.events.length, () => {
  const events = logStore.events
  if (events.length === 0) {
    systemStore.setPhase('IDLE')
    return
  }

  const lastEvent = events[events.length - 1]
  if (!lastEvent) {
    systemStore.setPhase('IDLE')
    return
  }

  let newPhase: AgentPhase = 'IDLE'

  if (lastEvent.type === 'ASK_USER') {
    newPhase = 'WAITING'
  } else if (lastEvent.type === 'TOOL_START' || isAgentBusy.value) {
    newPhase = 'EXECUTING'
  } else if (lastEvent.type === 'THOUGHT') {
    newPhase = 'PLANNING'
  } else if (lastEvent.type === 'AGENT_MESSAGE' && (lastEvent.data.content || '').includes('Task completed')) {
    newPhase = 'REVIEWING'
  }

  if (systemStore.currentPhase !== newPhase) {
    systemStore.setPhase(newPhase)
  }
})

// Watch for file tree synchronization
watch(() => systemStore.fileTreeUpdatedAt, () => {
  showFileSyncTip.value = true
  if (syncTipTimer) clearTimeout(syncTipTimer)
  syncTipTimer = setTimeout(() => {
    showFileSyncTip.value = false
  }, 5000)
})

const stopAgent = () => {
  eventBusService.send('CANCEL', {})
  // Provide immediate UI feedback
  logStore.addEvent({
    type: 'SYSTEM_ERROR',
    eventId: 'cancel-' + Date.now(),
    timestamp: Date.now(),
    data: {
      code: 'USER_CANCELLED',
      message: 'Task was forcibly stopped by the user.',
      canRetry: false
    }
  })
}

const formatPath = (path: string) => {
  if (!path || path === 'Loading...') return path
  // Split path and keep last 3 segments to prevent it from getting too long
  const parts = path.split('/')
  if (parts.length > 3) {
    return '.../' + parts.slice(-3).join('/')
  }
  return path
}
</script>

<template>
  <div
    v-if="systemStore.status !== 'CONNECTED'"
    class="bg-amber-500/10 border-b border-amber-500/20 px-4 py-1.5 flex items-center justify-center gap-2 animate-pulse"
  >
    <span class="w-1.5 h-1.5 bg-amber-500 rounded-full"></span>
    <span class="text-[10px] font-bold text-amber-500 uppercase tracking-tighter">
      {{ systemStore.status === 'RECONNECTING' ? 'Reconnecting to Ganglia Core...' : 'Disconnected' }}
    </span>
  </div>

  <div class="h-12 border-b border-slate-900 bg-slate-950/50 backdrop-blur flex items-center justify-between px-6 sticky top-0 z-10">
    <div class="flex items-center gap-6">
      <div class="flex items-center gap-2">
        <div class="w-2 h-2 rounded-full" :class="systemStore.status === 'CONNECTED' ? 'bg-emerald-500 shadow-[0_0_8px_rgba(16,185,129,0.5)]' : 'bg-slate-700'"></div>
        <span class="text-[10px] font-mono text-slate-500 uppercase tracking-widest">Core Status</span>
      </div>
      
      <!-- Phase Indicator -->
      <div class="flex items-center gap-1.5 border-l border-slate-800 pl-6 py-1">
        <span class="text-[10px] font-mono text-slate-500 uppercase tracking-widest">Phase</span>
        <span 
          class="text-[10px] font-bold px-2 py-0.5 rounded border"
          :class="{
            'bg-slate-800 text-slate-400 border-slate-700': systemStore.currentPhase === 'IDLE',
            'bg-blue-900/30 text-blue-400 border-blue-800/50': systemStore.currentPhase === 'PLANNING',
            'bg-amber-900/30 text-amber-400 border-amber-800/50': systemStore.currentPhase === 'EXECUTING',
            'bg-purple-900/30 text-purple-400 border-purple-800/50': systemStore.currentPhase === 'WAITING',
            'bg-emerald-900/30 text-emerald-400 border-emerald-800/50': systemStore.currentPhase === 'REVIEWING'
          }"
        >
          {{ systemStore.currentPhase }}
        </span>
      </div>

      <!-- Workspace Path -->
      <div class="flex items-center gap-2 border-l border-slate-800 pl-6 py-1 max-w-[300px]" title="Current Workspace">
        <span class="text-[10px] font-mono text-slate-500 uppercase tracking-widest">Workspace</span>
        <span class="text-[11px] font-mono text-slate-300 truncate bg-slate-900 px-2 py-0.5 rounded border border-slate-800">
          {{ formatPath(systemStore.workspacePath) }}
        </span>
      </div>

      <!-- File Sync Tip -->
      <transition
        enter-active-class="transition duration-300 ease-out"
        enter-from-class="transform -translate-x-4 opacity-0"
        enter-to-class="transform translate-x-0 opacity-100"
        leave-active-class="transition duration-500 ease-in"
        leave-from-class="opacity-100"
        leave-to-class="opacity-0"
      >
        <div v-if="showFileSyncTip" class="flex items-center gap-2 border-l border-slate-800 pl-6 py-1 animate-in fade-in slide-in-from-left-4">
          <span class="w-1.5 h-1.5 bg-emerald-500 rounded-full animate-ping"></span>
          <span class="text-[10px] font-bold text-emerald-500 uppercase tracking-widest">File tree synchronized</span>
        </div>
      </transition>
    </div>

    <button
      @click="stopAgent"
      class="flex items-center gap-2 px-3 py-1 rounded border transition-all active:scale-95 group"
      :class="isAgentBusy ? 'border-rose-500/80 bg-rose-500/20 hover:bg-rose-500/30 text-rose-400 shadow-[0_0_15px_rgba(244,63,94,0.3)] animate-pulse' : 'border-rose-500/30 bg-rose-500/5 hover:bg-rose-500/10 text-rose-500/70'"
    >
      <div class="w-2 h-2 bg-rose-500 rounded-sm group-hover:scale-110 transition-transform"></div>
      <span class="text-[10px] font-bold uppercase tracking-wider">Stop Agent</span>
    </button>
  </div>
</template>
