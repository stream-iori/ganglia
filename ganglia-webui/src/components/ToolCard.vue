<script setup lang="ts">
import { computed } from 'vue'
import { useSystemStore } from '../stores/system'
import { useLogStore } from '../stores/log'
import type { ServerEvent, ToolStartData, ToolResultData } from '../types'

const systemStore = useSystemStore()
const logStore = useLogStore()
const props = defineProps<{
  event: ServerEvent<ToolStartData>
  allEvents: ServerEvent[]
}>()

// Find the result event associated with this start event
const resultEvent = computed(() => {
  return props.allEvents.find(e => 
    e.type === 'TOOL_RESULT' && 
    (e.data as ToolResultData).toolCallId === props.event.data.toolCallId
  ) as ServerEvent<ToolResultData> | undefined
})

const isRunning = computed(() => !resultEvent.value)
const isError = computed(() => resultEvent.value?.data.isError || (resultEvent.value?.data.exitCode !== 0 && !isRunning.value))

const toolName = computed(() => props.event.data.toolName)
const command = computed(() => props.event.data.command)
const summary = computed(() => resultEvent.value?.data.summary || 'Executing...')

const ttyLineCount = computed(() => {
  const lines = logStore.activeToolCalls[props.event.data.toolCallId]
  return lines ? lines.length : 0
})

// Make paths in commands clickable
const handleCommandClick = (_e: MouseEvent) => {
  const selection = window.getSelection()?.toString().trim()
  if (selection) {
    const pathRegex = /^([a-zA-Z0-9_\-.]+\/)*[a-zA-Z0-9_\-.]+\.[a-z0-9]+$/
    if (pathRegex.test(selection)) {
      systemStore.toggleFileInspector(selection)
    }
  }
}
</script>

<template>
  <div 
    class="rounded-lg border bg-slate-900/50 overflow-hidden transition-all duration-300"
    :class="{
      'border-slate-800': !isError && !isRunning,
      'border-amber-500/50 shadow-[0_0_15px_rgba(245,158,11,0.15)]': isRunning,
      'border-rose-500/50 shadow-[0_0_15px_rgba(244,63,94,0.15)]': isError
    }"
  >
    <div class="px-4 py-2 flex items-center justify-between bg-slate-900">
      <div class="flex items-center gap-3">
        <span v-if="isRunning" class="w-2 h-2 bg-amber-500 rounded-full animate-pulse"></span>
        <span v-else-if="isError" class="text-rose-500 text-xs font-bold">✕</span>
        <span v-else class="text-emerald-500 text-xs font-bold">✓</span>
        
        <span class="text-[10px] font-mono font-bold uppercase tracking-wider text-slate-400">
          {{ toolName }}
        </span>
      </div>
      
      <div class="flex gap-2 items-center">
        <span v-if="ttyLineCount > 0" class="text-[9px] font-mono text-slate-500 bg-slate-950 px-1.5 py-0.5 rounded border border-slate-800">
          {{ ttyLineCount }} lines
        </span>
        <button 
          @click.stop="systemStore.toggleInspector(event.data.toolCallId, 'TERMINAL')"
          class="text-[10px] text-slate-500 hover:text-slate-300 underline font-mono transition-colors"
        >
          Logs
        </button>
      </div>
    </div>
    
    <div class="px-4 py-3">
      <div 
        @click="handleCommandClick"
        class="text-xs font-mono text-slate-300 bg-slate-950 p-2.5 rounded mb-2 border border-slate-800/50 overflow-x-auto whitespace-nowrap text-wrap cursor-text group"
        title="Double-click a path to inspect"
      >
        <span class="text-slate-600 mr-2 select-none">$</span>{{ command }}
      </div>
      
      <div class="text-[11px] text-slate-400 leading-relaxed">
        {{ summary }}
      </div>
    </div>
  </div>
</template>
