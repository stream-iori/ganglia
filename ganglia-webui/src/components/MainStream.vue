<script setup lang="ts">
import { ref, onUpdated, computed, watch } from 'vue'
import { eventBusService } from '../services/eventbus'
import { useLogStore } from '../stores/log'
import { useSystemStore } from '../stores/system'
import ThoughtCard from './ThoughtCard.vue'
import ToolCard from './ToolCard.vue'
import AgentMessage from './AgentMessage.vue'
import AskUserForm from './AskUserForm.vue'
import TaskReviewCard from './TaskReviewCard.vue'
import StatusBar from './StatusBar.vue'

const logStore = useLogStore()
const systemStore = useSystemStore()
const prompt = ref('')
const streamEnd = ref<HTMLElement | null>(null)

let isScrolledToBottom = true
const hasNewContent = ref(false)

const handleScroll = (e: Event) => {
  const target = e.target as HTMLElement
  // Check if we are within 50px of the bottom
  isScrolledToBottom = Math.abs(target.scrollHeight - target.clientHeight - target.scrollTop) < 50
  if (isScrolledToBottom) {
    hasNewContent.value = false
  }
}

const scrollToBottom = () => {
  isScrolledToBottom = true
  hasNewContent.value = false
  streamEnd.value?.scrollIntoView({ behavior: 'smooth' })
}

// Watch for new content when scrolled up
watch([() => logStore.events.length, () => logStore.streamingMessage], () => {
  if (!isScrolledToBottom) {
    hasNewContent.value = true
  }
})

// Context Injection Logic
watch(() => systemStore.pendingContextPath, (path) => {
  if (path) {
    const mention = `@${path} `
    if (!prompt.value.includes(mention)) {
      prompt.value = (prompt.value.trim() ? prompt.value.trim() + ' ' : '') + mention
    }
    systemStore.clearPendingContext()
  }
})

const isBlocked = computed(() => {
  return !!systemStore.activeAskId
})

const typingStatus = computed(() => {
  // If we are streaming, try to infer the context based on the last tool used,
  // or default to thinking.
  const lastEvent = logStore.events[logStore.events.length - 1]
  if (lastEvent?.type === 'TOOL_START' && !logStore.events.find(e => e.type === 'TOOL_RESULT' && e.data.toolCallId === lastEvent.data.toolCallId)) {
     const name = lastEvent.data.toolName
     if (['read_file', 'glob', 'list_directory'].includes(name)) return 'Agent is reading files...'
     if (name === 'run_shell_command') return 'Agent is executing a command...'
     if (['write_file', 'replace'].includes(name)) return 'Agent is writing code...'
     return `Agent is using ${name}...`
  }
  
  if (logStore.streamingThought) return 'Agent is thinking...'
  if (logStore.streamingMessage) return 'Agent is responding...'
  
  return 'Agent is thinking...'
})

const sendMessage = () => {
  if (!prompt.value.trim() || isBlocked.value) return
  eventBusService.send('START', { prompt: prompt.value })
  prompt.value = ''
  isScrolledToBottom = true // Force scroll to bottom when sending a message
}

const retry = () => {
  eventBusService.send('RETRY', {})
}

onUpdated(() => {
  if (isScrolledToBottom) {
    streamEnd.value?.scrollIntoView({ behavior: 'smooth' })
  }
})
</script>

<template>
  <main class="flex-1 flex flex-col bg-slate-950 h-full relative overflow-hidden">
    <StatusBar />

    <!-- Message Stream -->
    <div 
      class="flex-1 overflow-y-auto p-6 scrollbar-thin scrollbar-thumb-slate-800 relative"
      ref="streamContainer"
      @scroll="handleScroll"
    >
      <!-- Blocking Overlay -->

      <div
        v-if="isBlocked"
        class="absolute inset-0 bg-slate-950/40 backdrop-blur-[1px] z-10 transition-all duration-500"
      ></div>

      <div class="max-w-3xl mx-auto space-y-8 pb-20 relative z-20">
        <!-- Welcome Message if empty -->
        <div v-if="logStore.events.length === 0" class="text-slate-500 text-center py-20">
          <div class="text-4xl mb-4 opacity-20 text-emerald-500">⚛</div>
          <p class="text-sm font-medium text-slate-300">Ganglia TUI Pro</p>
          <p class="text-[10px] opacity-40 mt-1">Autonomous Coding Agent Navigator</p>
        </div>

        <!-- Dynamic Event Stream -->
        <template v-for="event in logStore.events" :key="event.eventId">

          <div v-if="event.type === 'USER_MESSAGE'" class="flex justify-end">
            <div class="bg-slate-800 text-slate-100 px-4 py-2 rounded-2xl rounded-tr-none max-w-[80%] shadow-md border border-slate-700/50">
              {{ event.data.content }}
            </div>
          </div>

          <ThoughtCard 
            v-if="event.type === 'THOUGHT'" 
            :content="event.data.content" 
            v-show="event.data.content !== '...' || !logStore.streamingThought"
          />

          <ToolCard v-if="event.type === 'TOOL_START'" :event="event" :all-events="logStore.events" />

          <AgentMessage v-if="event.type === 'AGENT_MESSAGE'" :content="event.data.content" />

          <!-- Render TaskReviewCard right after the final completion message -->
          <TaskReviewCard v-if="event.type === 'AGENT_MESSAGE' && event.data.content.includes('Task completed')" />

          <AskUserForm v-if="event.type === 'ASK_USER'" :event="event" />

          <div v-if="event.type === 'SYSTEM_ERROR'" class="bg-rose-950/20 border border-rose-500/50 p-4 rounded-lg text-rose-200 text-xs shadow-lg">
            <div class="font-bold mb-1 uppercase tracking-tight flex items-center justify-between">
              <span>System Error: {{ event.data.code }}</span>
              <button
                v-if="event.data.canRetry"
                @click="retry"
                class="bg-rose-500 hover:bg-rose-400 text-white px-2 py-0.5 rounded text-[10px] transition-colors"
              >
                Retry Last Command
              </button>
            </div>
            {{ event.data.message }}
          </div>

        </template>

        <!-- Live Streaming Thought -->
        <div v-if="logStore.streamingThought" class="animate-in fade-in duration-300">
          <div class="flex items-center gap-2 mb-2 text-slate-500">
            <span class="w-1.5 h-1.5 bg-amber-500 rounded-full animate-pulse"></span>
            <span class="text-[10px] font-bold uppercase tracking-widest">Agent is thinking...</span>
          </div>
          <ThoughtCard :content="logStore.streamingThought" :initially-expanded="true" />
        </div>

        <!-- Live Streaming Message -->
        <div v-if="logStore.streamingMessage" class="animate-in fade-in duration-300">
          <div class="flex items-center gap-2 mb-2 text-slate-500">
            <span class="w-1.5 h-1.5 bg-emerald-500 rounded-full animate-pulse"></span>
            <span class="text-[10px] font-bold uppercase tracking-widest">{{ typingStatus }}</span>
          </div>
          <AgentMessage :content="logStore.streamingMessage" />
        </div>

        <div ref="streamEnd" class="h-1"></div>
      </div>
    </div>

    <!-- Input Area -->
    <div class="p-6 bg-gradient-to-t from-slate-950 via-slate-950/95 to-transparent border-t border-slate-900/50 relative">
      <!-- Scroll to bottom FAB -->
      <transition
        enter-active-class="transition duration-300 ease-out"
        enter-from-class="transform translate-y-4 opacity-0"
        enter-to-class="transform translate-y-0 opacity-100"
        leave-active-class="transition duration-200 ease-in"
        leave-from-class="transform translate-y-0 opacity-100"
        leave-to-class="transform translate-y-4 opacity-0"
      >
        <button 
          v-if="hasNewContent"
          @click="scrollToBottom"
          class="absolute -top-12 left-1/2 -translate-x-1/2 flex items-center gap-2 px-4 py-2 bg-emerald-600 hover:bg-emerald-500 text-white rounded-full shadow-2xl shadow-emerald-900/20 text-xs font-bold transition-all z-30"
        >
          <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><path d="M12 5v14M5 12l7 7 7-7"/></svg>
          New Messages
          <span class="w-2 h-2 bg-white rounded-full animate-ping"></span>
        </button>
      </transition>

      <div class="max-w-3xl mx-auto relative group">
        <textarea
          v-model="prompt"
          :disabled="isBlocked"
          @keydown.enter.exact.prevent="sendMessage"
          :placeholder="isBlocked ? 'Decision required above...' : 'Type a command or ask a question...'"
          class="w-full bg-slate-900/80 border border-slate-800 rounded-xl pl-4 pr-24 py-4 text-slate-200 focus:outline-none focus:border-emerald-500/50 focus:bg-slate-900 transition-all resize-none h-20 shadow-2xl disabled:opacity-50 disabled:cursor-not-allowed"
        ></textarea>

        <div class="absolute right-3 top-1/2 -translate-y-1/2 flex items-center gap-2">
          <kbd v-if="!isBlocked" class="text-[10px] text-slate-600 bg-slate-950 px-1.5 py-0.5 rounded border border-slate-800 font-mono text-xs">Enter</kbd>
          <button
            @click="sendMessage"
            :disabled="isBlocked"
            class="bg-emerald-600 hover:bg-emerald-500 text-white p-2 rounded-lg transition-all active:scale-95 shadow-lg disabled:bg-slate-800 disabled:text-slate-600"
          >
            <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="22" y1="2" x2="11" y2="13"></line><polygon points="22 2 15 22 11 13 2 9 22 2"></polygon></svg>
          </button>
        </div>
      </div>
    </div>
  </main>
</template>
