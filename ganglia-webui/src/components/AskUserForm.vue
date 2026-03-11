<script setup lang="ts">
import { computed } from 'vue'
import { eventBusService } from '../services/eventbus'
import { useSystemStore } from '../stores/system'
import type { ServerEvent, AskUserData } from '../types'
import VueMarkdown from 'vue-markdown-render'

const props = defineProps<{
  event: ServerEvent<AskUserData>
}>()

const systemStore = useSystemStore()

const isActive = computed(() => systemStore.activeAskId === props.event.data.askId)

const selectOption = (optionValue: string) => {
  eventBusService.send('RESPOND_ASK', {
    askId: props.event.data.askId,
    selectedOption: optionValue
  })
}

const diffMarkdown = computed(() => {
  if (!props.event.data.diffContext) return ''
  return `\`\`\`diff\n${props.event.data.diffContext}\n\`\`\``
})

const markdownOptions = {
  html: true,
  linkify: true,
  typographer: true,
}
</script>

<template>
  <div v-if="isActive" class="fixed inset-0 bg-slate-950/80 backdrop-blur-md z-50 flex items-center justify-center p-4">
    <div class="max-w-4xl w-full bg-slate-900 border-2 border-amber-500 rounded-2xl overflow-hidden shadow-[0_0_50px_rgba(245,158,11,0.3)] animate-in zoom-in-95 duration-200 max-h-[90vh] flex flex-col">
      <div class="bg-amber-500/10 px-8 py-5 border-b border-amber-500/20 flex items-center gap-4 flex-shrink-0">
        <span class="text-amber-500 text-3xl">⚠️</span>
        <div>
          <h3 class="text-lg font-bold text-amber-500 uppercase tracking-tight">Authorization Required</h3>
          <p class="text-sm text-slate-400 mt-0.5">The agent needs your decision to continue.</p>
        </div>
      </div>
      
      <div class="p-8 overflow-y-auto flex-1 scrollbar-thin scrollbar-thumb-slate-800">
        <p class="text-slate-100 text-base mb-6 leading-relaxed">
          {{ event.data.question }}
        </p>

        <div v-if="event.data.diffContext" class="mb-8 border border-slate-700/50 rounded-lg overflow-hidden bg-[#0d1117]">
          <div class="bg-slate-800/50 px-4 py-2 border-b border-slate-700/50 flex items-center justify-between">
            <span class="text-xs font-mono text-slate-400">Context / Diff</span>
          </div>
          <div class="p-4 overflow-x-auto prose prose-invert prose-sm max-w-none prose-pre:m-0 prose-pre:bg-transparent prose-pre:p-0">
            <VueMarkdown 
              :source="diffMarkdown" 
              :options="markdownOptions"
            />
          </div>
        </div>
        
        <div 
          class="grid gap-4 mt-auto"
          :class="event.data.options.length > 3 ? 'grid-cols-1 sm:grid-cols-2' : 'grid-cols-1'"
        >
          <button 
            v-for="option in event.data.options" 
            :key="option.value"
            @click="selectOption(option.value)"
            class="flex flex-col items-start p-5 rounded-xl border border-slate-700 bg-slate-800/50 hover:border-emerald-500/50 hover:bg-slate-800 transition-all text-left group"
          >
            <span class="text-base font-semibold text-slate-200 group-hover:text-emerald-400">{{ option.label }}</span>
            <span class="text-sm text-slate-400 mt-1 leading-relaxed">{{ option.description }}</span>
          </button>
        </div>
      </div>
    </div>
  </div>

  <div v-else class="my-6 bg-slate-900 border-2 border-amber-500/50 rounded-xl overflow-hidden shadow-[0_0_30px_rgba(245,158,11,0.15)] opacity-60">
    <!-- Historical view -->
    <div class="bg-slate-800/50 px-4 py-2 border-b border-slate-700/50 flex items-center gap-2">
      <span class="text-amber-500/50 text-xs">⚠️ Historical Ask</span>
    </div>
    <div class="p-4">
      <p class="text-slate-400 text-xs italic mb-2">
        "{{ event.data.question }}"
      </p>
      <div v-if="event.data.diffContext" class="text-[10px] font-mono text-slate-500 line-clamp-3 bg-black/30 p-2 rounded">
        {{ event.data.diffContext }}
      </div>
    </div>
  </div>
</template>
