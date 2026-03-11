<script setup lang="ts">
import { ref } from 'vue'

const props = defineProps<{
  content: string
  initiallyExpanded?: boolean
}>()

const isExpanded = ref(props.initiallyExpanded || false)
</script>

<template>
  <div class="border-l-2 border-slate-700 pl-4 py-1 my-2">
    <button 
      @click="isExpanded = !isExpanded"
      class="text-[10px] uppercase tracking-widest text-slate-500 hover:text-slate-300 flex items-center gap-2 transition-colors"
    >
      <span :class="{ 'rotate-90': isExpanded }" class="transition-transform inline-block">▶</span>
      Thought Process
    </button>
    
    <div v-if="isExpanded" class="mt-2 text-sm text-slate-400 italic leading-relaxed animate-in fade-in slide-in-from-top-1 duration-200">
      {{ content }}
    </div>
  </div>
</template>
