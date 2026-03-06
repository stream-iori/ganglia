<script setup lang="ts">
import { ref } from 'vue'
import type { FileTreeNode } from '../types'
import { useSystemStore } from '../stores/system'

const props = defineProps<{
  node: FileTreeNode
  depth: number
}>()

const systemStore = useSystemStore()
const isExpanded = ref(false)

const toggle = () => {
  if (props.node.type === 'directory') {
    isExpanded.value = !isExpanded.value
  } else {
    systemStore.toggleFileInspector(props.node.path)
  }
}
</script>

<template>
  <div class="select-none">
    <div 
      @click="toggle"
      class="flex items-center gap-2 py-1 px-2 hover:bg-slate-800 rounded cursor-pointer transition-colors group"
      :style="{ paddingLeft: `${depth * 12 + 8}px` }"
    >
      <span v-if="node.type === 'directory'" class="text-slate-500 group-hover:text-slate-300 transition-transform duration-200" :class="{ 'rotate-90': isExpanded }">
        ▶
      </span>
      <span v-else class="text-slate-600 text-[10px]">
        📄
      </span>
      
      <span class="text-xs truncate" :class="node.type === 'directory' ? 'text-slate-300 font-medium' : 'text-slate-400'">
        {{ node.name }}
      </span>
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
