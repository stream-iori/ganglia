<script setup lang="ts">
import VueMarkdown from 'vue-markdown-render'
import { useSystemStore } from '../stores/system'
// @ts-ignore
import { full as emoji } from 'markdown-it-emoji'
// @ts-ignore
import taskLists from 'markdown-it-task-lists'

defineProps<{
  content: string
}>()

const systemStore = useSystemStore()

const markdownOptions = {
  html: true,
  linkify: true,
  typographer: true,
}

const markdownPlugins: any[] = [] // Temporarily disabled to fix TypeError: e.apply is not a function

// Simple heuristic to detect if a click is on a potential file path
const handleContentClick = (event: MouseEvent) => {
  const target = event.target as HTMLElement
  if (target.tagName === 'CODE' || target.tagName === 'SPAN') {
    const text = target.innerText.trim()
    const pathRegex = /^([a-zA-Z0-9_\-.]+\/)*[a-zA-Z0-9_\-.]+\.[a-z0-9]+$/
    if (pathRegex.test(text)) {
      systemStore.toggleFileInspector(text)
    }
  }
}
</script>

<template>
  <div class="space-y-2">
    <div class="flex items-center gap-2 text-emerald-500/70 text-[10px] font-bold uppercase tracking-widest mb-1">
      <span class="w-1.5 h-1.5 bg-emerald-500 rounded-full"></span>
      Agent
    </div>
    <div 
      class="prose prose-invert prose-sm max-w-none text-slate-200 cursor-default"
      @click="handleContentClick"
    >
      <div v-if="!content" class="text-rose-500 text-[10px] italic opacity-50">
        [DEBUG] Received empty content for Agent Message
      </div>
      <VueMarkdown 
        v-else
        :source="String(content)" 
        :options="markdownOptions"
        :plugins="markdownPlugins"
      />
    </div>
  </div>
</template>

<style>
/* Style markdown specific elements */
.prose pre {
  background-color: #020617 !important; /* slate-950 */
  border: 1px solid #1e293b; /* slate-800 */
}
.prose code {
  color: #34d399; /* emerald-400 */
  cursor: pointer;
}
.prose code:hover {
  text-decoration: underline;
}
/* Table styles for GFM */
.prose table {
  width: 100%;
  border-collapse: collapse;
  margin: 1rem 0;
}
.prose th, .prose td {
  border: 1px solid #334155;
  padding: 0.5rem;
  text-align: left;
}
.prose th {
  background-color: #1e293b;
}
</style>
