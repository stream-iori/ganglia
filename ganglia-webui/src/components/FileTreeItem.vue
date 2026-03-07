<script setup lang="ts">
import { ref, computed } from 'vue'
import type { FileTreeNode } from '../types'
import { useSystemStore } from '../stores/system'

const props = defineProps<{
  node: FileTreeNode
  depth: number
}>()

const systemStore = useSystemStore()
const isExpanded = ref(false)

// Optional: In a real implementation, you would check against a list of changed/new paths in a store
const isHighlighted = computed(() => {
  return props.node.type === 'file' && systemStore.modifiedPaths.has(props.node.path)
})

const toggle = () => {
  if (props.node.type === 'directory') {
    isExpanded.value = !isExpanded.value
  } else {
    systemStore.toggleFileInspector(props.node.path)
  }
}

const addContext = (e: MouseEvent) => {
  e.stopPropagation()
  systemStore.addContextToPrompt(props.node.path)
}
</script>

<template>
  <div class="select-none">
    <div 
      @click="toggle"
      class="flex items-center gap-2 py-1 px-2 hover:bg-slate-800 rounded cursor-pointer transition-colors group relative"
      :style="{ paddingLeft: `${depth * 12 + 8}px` }"
      :class="{'bg-emerald-900/10 hover:bg-emerald-900/30': isHighlighted}"
    >
      <div v-if="isHighlighted" class="absolute left-0 top-1/2 -translate-y-1/2 w-0.5 h-3/4 bg-emerald-500 rounded-r"></div>
      
      <span v-if="node.type === 'directory'" class="text-slate-500 group-hover:text-slate-300 transition-transform duration-200" :class="{ 'rotate-90': isExpanded }">
        ▶
      </span>
      <span v-else class="text-slate-600 text-[10px]">
        📄
      </span>
      
      <span class="text-xs truncate flex-1" :class="[node.type === 'directory' ? 'font-medium' : '', isHighlighted ? 'text-emerald-400 font-medium' : 'text-slate-400 group-hover:text-slate-300']">
        {{ node.name }}
      </span>

      <!-- Mention / Context Button -->
      <button 
        v-if="node.type === 'file'"
        @click="addContext"
        class="opacity-0 group-hover:opacity-100 p-0.5 hover:bg-slate-700 rounded text-slate-500 hover:text-emerald-400 transition-all"
        title="Add to context"
      >
        <svg xmlns="http://www.w3.org/2000/svg" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M12 5v14M5 12h14"/></svg>
      </button>
    </div>
    
    <div v-if="isExpanded && node.children">
      <FileTreeItem 
        v-for="child in node.children" 
        :key="child.path" 
        :node="child" 
        :depth="depth + 1" 
      />
    </div>
  </div>
</template>
