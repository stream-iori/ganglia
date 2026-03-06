<script setup lang="ts">
import { eventBusService } from '../services/eventbus'
import type { ServerEvent, AskUserData } from '../types'

const props = defineProps<{
  event: ServerEvent<AskUserData>
}>()

const selectOption = (optionValue: string) => {
  eventBusService.send('RESPOND_ASK', {
    askId: props.event.data.askId,
    selectedOption: optionValue
  })
}
</script>

<template>
  <div class="my-6 bg-slate-900 border-2 border-amber-500/50 rounded-xl overflow-hidden shadow-[0_0_30px_rgba(245,158,11,0.15)]">
    <div class="bg-amber-500/10 px-6 py-4 border-b border-amber-500/20 flex items-center gap-3">
      <span class="text-amber-500 text-xl">⚠️</span>
      <div>
        <h3 class="text-sm font-bold text-amber-500 uppercase tracking-tight">Authorization Required</h3>
        <p class="text-xs text-slate-400 mt-0.5">The agent needs your decision to continue.</p>
      </div>
    </div>
    
    <div class="p-6">
      <p class="text-slate-200 text-sm mb-6 leading-relaxed">
        {{ event.data.question }}
      </p>
      
      <div class="grid grid-cols-1 gap-3">
        <button 
          v-for="option in event.data.options" 
          :key="option.value"
          @click="selectOption(option.value)"
          class="flex flex-col items-start p-4 rounded-lg border border-slate-800 bg-slate-950 hover:border-emerald-500/50 hover:bg-slate-900 transition-all text-left group"
        >
          <span class="text-sm font-semibold text-slate-200 group-hover:text-emerald-400">{{ option.label }}</span>
          <span class="text-xs text-slate-500 mt-1">{{ option.description }}</span>
        </button>
      </div>
    </div>
  </div>
</template>
