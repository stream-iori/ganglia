<script setup lang="ts">
import { useSystemStore } from '../stores/system'
import { useLogStore } from '../stores/log'
import { eventBusService } from '../services/eventbus'
import FileTreeItem from './FileTreeItem.vue'

const systemStore = useSystemStore()
const logStore = useLogStore()

const refreshFiles = () => {
  eventBusService.send('LIST_FILES', {})
}

const newSession = () => {
  if (confirm('Start a new session? Current history will be hidden until you switch back.')) {
    localStorage.removeItem('ganglia_session_id')
    window.location.reload()
  }
}
</script>

<template>
  <aside class="w-64 bg-slate-900 text-slate-300 flex flex-col h-full border-r border-slate-800">
    <!-- Header -->
    <div class="p-4 border-b border-slate-800 flex justify-between items-start">
      <div>
        <h1 class="text-xl font-bold text-white flex items-center gap-2">
          <span class="text-emerald-500 font-mono">⚛</span> Ganglia
        </h1>
        <div class="mt-2 text-[10px] flex items-center gap-2 uppercase tracking-tighter font-bold">
          <span class="w-1.5 h-1.5 rounded-full" :class="{
            'bg-emerald-500 shadow-[0_0_5px_rgba(16,185,129,0.5)]': systemStore.status === 'CONNECTED',
            'bg-amber-500': systemStore.status === 'RECONNECTING',
            'bg-rose-500': systemStore.status === 'DISCONNECTED'
          }"></span>
          <span :class="systemStore.status === 'CONNECTED' ? 'text-slate-200' : 'text-slate-500'">
            {{ systemStore.status }}
          </span>
        </div>
      </div>
      
      <button 
        @click="newSession"
        class="text-slate-600 hover:text-emerald-500 transition-colors p-1"
        title="New Session"
      >
        <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 5v14M5 12h14"/></svg>
      </button>
    </div>
    
    <!-- Navigation / Context -->
    <div class="flex-1 overflow-y-auto custom-scrollbar">
      <div class="p-4">
        <div class="mb-6">
          <div class="flex items-center justify-between mb-3">
            <h2 class="text-[10px] font-bold uppercase tracking-widest text-slate-500">Workspace</h2>
            <button 
              @click="refreshFiles"
              class="text-slate-600 hover:text-slate-300 transition-colors"
              title="Refresh file tree"
            >
              <svg xmlns="http://www.w3.org/2000/svg" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><path d="M21 2v6h-6"></path><path d="M3 12a9 9 0 0 1 15-6.7L21 8"></path><path d="M3 22v-6h6"></path><path d="M21 12a9 9 0 0 1-15 6.7L3 16"></path></svg>
            </button>
          </div>
          
          <div v-if="logStore.fileTree">
            <FileTreeItem :node="logStore.fileTree" :depth="0" />
          </div>
          <div v-else class="text-[10px] opacity-40 italic px-2">
            Loading project structure...
          </div>
        </div>
        
        <div class="mb-6">
          <h2 class="text-[10px] font-bold uppercase tracking-widest text-slate-500 mb-3">Active Session</h2>
          <div class="text-[10px] font-mono break-all bg-slate-950 p-2.5 rounded border border-slate-800 text-slate-400">
            {{ systemStore.sessionId }}
          </div>
        </div>
      </div>
    </div>
    
    <!-- Footer -->
    <div class="p-4 bg-slate-950 border-t border-slate-800 flex items-center justify-between">
      <span class="text-[9px] text-slate-600 font-mono tracking-tighter">V1.1.0-WEBUI</span>
      <div class="flex gap-2">
        <div class="w-1.5 h-1.5 rounded-full bg-slate-800"></div>
        <div class="w-1.5 h-1.5 rounded-full bg-slate-800"></div>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.custom-scrollbar::-webkit-scrollbar {
  width: 4px;
}
.custom-scrollbar::-webkit-scrollbar-thumb {
  background: #1e293b;
}
</style>
