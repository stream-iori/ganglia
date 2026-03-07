<script setup lang="ts">
import { computed, ref, watch, onMounted, onUnmounted } from 'vue'
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

const isRecovering = computed(() => {
  if (!isError.value) return false
  
  const toolResults = props.allEvents.filter(e => e.type === 'TOOL_RESULT')
  const lastToolResult = toolResults[toolResults.length - 1]
  
  if (lastToolResult && (lastToolResult.data as ToolResultData).toolCallId === props.event.data.toolCallId) {
    // This is the latest tool result. If it's an error, check if the agent has yielded control.
    const resultIndex = props.allEvents.findIndex(e => e.eventId === lastToolResult.eventId)
    const subsequentEvents = props.allEvents.slice(resultIndex + 1)
    
    // If agent sent a final message, asked a user, or a new tool started, then this specific tool is no longer "recovering"
    const hasYielded = subsequentEvents.some(e => e.type === 'AGENT_MESSAGE' || e.type === 'ASK_USER' || e.type === 'SYSTEM_ERROR')
    
    return !hasYielded
  }
  return false
})

const toolName = computed(() => props.event.data.toolName)
const command = computed(() => props.event.data.command)
const summary = computed(() => resultEvent.value?.data.summary || 'Executing...')

const ttyLineCount = computed(() => {
  const lines = logStore.activeToolCalls[props.event.data.toolCallId]
  return lines ? lines.length : 0
})

const isMiniCommand = computed(() => {
  if (toolName.value === 'run_shell_command') {
    const cmd = command.value?.trim() || ''
    return /^(ls|pwd|whoami|cat|git status|git branch|echo|mkdir|rm)(\s|$)/.test(cmd)
  }
  return ['list_directory', 'read_file', 'glob'].includes(toolName.value)
})

const isExpanded = ref(!isMiniCommand.value)

// Execution Timer
const startTime = ref(Date.now())
const executionTime = ref(0)
let timerInterval: any = null

const startTimer = () => {
  timerInterval = setInterval(() => {
    executionTime.value = Math.floor((Date.now() - startTime.value) / 1000)
  }, 1000)
}

onMounted(() => {
  if (isRunning.value) {
    startTimer()
  } else if (resultEvent.value) {
    if (props.event.timestamp && resultEvent.value.timestamp) {
      executionTime.value = Math.floor((resultEvent.value.timestamp - props.event.timestamp) / 1000)
    }
  }
})

watch(isRunning, (newVal) => {
  if (!newVal) { // Task finished
    if (timerInterval) clearInterval(timerInterval)
    if (props.event.timestamp && resultEvent.value?.timestamp) {
      executionTime.value = Math.floor((resultEvent.value.timestamp - props.event.timestamp) / 1000)
    } else {
      executionTime.value = Math.floor((Date.now() - startTime.value) / 1000)
    }
    
    // Auto-collapse if it succeeded
    if (!isError.value) {
      isExpanded.value = false
    }
  }
})

onUnmounted(() => {
  if (timerInterval) clearInterval(timerInterval)
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
    class="rounded-lg border bg-slate-900/50 overflow-hidden transition-all duration-300 cursor-pointer"
    :class="{
      'border-slate-800': !isError && !isRunning,
      'border-amber-500/50 shadow-[0_0_15px_rgba(245,158,11,0.15)]': isRunning,
      'border-rose-500/50 shadow-[0_0_15px_rgba(244,63,94,0.15)]': isError,
      'opacity-70 hover:opacity-100': !isRunning && !isExpanded
    }"
    @click="isExpanded = !isExpanded"
  >
    <div class="px-4 py-2 flex items-center justify-between" :class="isExpanded ? 'bg-slate-900 border-b border-slate-800/50' : 'bg-transparent'">
      <div class="flex items-center gap-3 overflow-hidden">
        <span v-if="isRunning" class="w-2 h-2 bg-amber-500 rounded-full animate-pulse flex-shrink-0"></span>
        <span v-else-if="isError" class="text-rose-500 text-xs font-bold flex-shrink-0">✕</span>
        <span v-else class="text-emerald-500 text-xs font-bold flex-shrink-0">✓</span>
        
        <span class="text-[10px] font-mono font-bold uppercase tracking-wider text-slate-400 flex-shrink-0">
          {{ toolName }}
        </span>

        <span v-if="!isExpanded" class="text-[10px] font-mono text-slate-500 truncate max-w-[300px] ml-2">
          {{ command }}
        </span>
      </div>
      
      <div class="flex gap-3 items-center flex-shrink-0">
        <!-- View Diff Button for modification tools -->
        <button 
          v-if="['replace', 'write_file'].includes(toolName) && resultEvent && !isError"
          @click.stop="systemStore.toggleDiffInspector(resultEvent.data.fullOutput || summary)"
          class="flex items-center gap-1 px-2 py-0.5 rounded bg-emerald-900/30 border border-emerald-800/50 text-emerald-400 hover:bg-emerald-900/50 transition-colors"
          title="Review Changes"
        >
          <span class="text-[10px]">👁️</span>
          <span class="text-[9px] font-bold uppercase tracking-wider">Diff</span>
        </button>

        <span v-if="executionTime > 0 || isRunning" class="text-[9px] font-mono" :class="isRunning ? 'text-amber-500 font-bold' : 'text-slate-600'">
          {{ isRunning ? 'running ' : '' }}{{ executionTime }}s
        </span>
        <span v-if="ttyLineCount > 0" class="text-[9px] font-mono text-slate-500 bg-slate-950 px-1.5 py-0.5 rounded border border-slate-800">
          {{ ttyLineCount }} lines
        </span>
        <button 
          v-if="isExpanded"
          @click.stop="systemStore.toggleInspector(event.data.toolCallId, 'TERMINAL')"
          class="text-[10px] text-slate-500 hover:text-slate-300 underline font-mono transition-colors"
        >
          Logs
        </button>
      </div>
    </div>
    
    <div v-if="isExpanded" class="px-4 py-3" @click.stop>
      <div 
        @click="handleCommandClick"
        class="text-xs font-mono text-slate-300 bg-slate-950 p-2.5 rounded mb-2 border border-slate-800/50 overflow-x-auto whitespace-nowrap text-wrap cursor-text group"
        title="Double-click a path to inspect"
      >
        <span class="text-slate-600 mr-2 select-none">$</span>{{ command }}
      </div>
      
      <div class="text-[11px] text-slate-400 leading-relaxed max-h-[200px] overflow-y-auto scrollbar-thin scrollbar-thumb-slate-800">
        {{ summary }}
      </div>
      
      <div v-if="isRecovering" class="mt-3 flex items-center gap-2 text-rose-400 bg-rose-950/30 p-2 rounded border border-rose-900/50">
        <span class="w-1.5 h-1.5 bg-rose-500 rounded-full animate-pulse"></span>
        <span class="text-[10px] font-mono">Agent is attempting to recover...</span>
      </div>
    </div>
  </div>
</template>
