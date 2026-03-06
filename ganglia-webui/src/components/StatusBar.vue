<script setup lang="ts">
import { useSystemStore } from '../stores/system'
import { eventBusService } from '../services/eventbus'

const systemStore = useSystemStore()

const stopAgent = () => {
  eventBusService.send('CANCEL', {})
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
    <div class="flex items-center gap-4">
      <div class="flex items-center gap-2">
        <div class="w-2 h-2 rounded-full" :class="systemStore.status === 'CONNECTED' ? 'bg-emerald-500 shadow-[0_0_8px_rgba(16,185,129,0.5)]' : 'bg-slate-700'"></div>
        <span class="text-[10px] font-mono text-slate-500 uppercase tracking-widest">Core Status</span>
      </div>
    </div>

    <button
      @click="stopAgent"
      class="flex items-center gap-2 px-3 py-1 rounded border border-rose-500/30 bg-rose-500/5 hover:bg-rose-500/10 text-rose-500 transition-all active:scale-95 group"
    >
      <div class="w-2 h-2 bg-rose-500 rounded-sm group-hover:scale-110 transition-transform"></div>
      <span class="text-[10px] font-bold uppercase tracking-wider">Stop Agent</span>
    </button>
  </div>
</template>
