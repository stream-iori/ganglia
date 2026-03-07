<script setup lang="ts">
import { computed, ref, watch, onMounted, nextTick } from 'vue'
import { useSystemStore } from '../stores/system'
import { useLogStore } from '../stores/log'
import { eventBusService } from '../services/eventbus'
import { useVirtualizer } from '@tanstack/vue-virtual'
import { createHighlighter } from 'shiki'

const systemStore = useSystemStore()
const logStore = useLogStore()

const parentRef = ref<HTMLElement | null>(null)
const highlighter = ref<any>(null)
const highlightedCode = ref('')
const highlightedDiff = ref('')

onMounted(async () => {
  highlighter.value = await createHighlighter({
    themes: ['github-dark'],
    langs: ['java', 'javascript', 'typescript', 'xml', 'json', 'bash', 'markdown', 'python', 'yaml', 'diff']
  })
})

const ttyLines = computed(() => {
  if (!systemStore.inspectorToolCallId) return []
  const allLines = logStore.activeToolCalls[systemStore.inspectorToolCallId] || []
  
  if (!systemStore.terminalSearchQuery) return allLines
  
  try {
    const regex = new RegExp(systemStore.terminalSearchQuery, 'i')
    return allLines.filter(line => regex.test(line))
  } catch (e) {
    // If invalid regex, just return all lines or handle as plain string
    return allLines.filter(line => line.toLowerCase().includes(systemStore.terminalSearchQuery.toLowerCase()))
  }
})

const rowVirtualizer = useVirtualizer({
  count: computed(() => ttyLines.value.length).value,
  getScrollElement: () => parentRef.value,
  estimateSize: () => 20,
  overscan: 10,
})

// Auto-scroll when new lines arrive if we're near the bottom
watch(() => ttyLines.value.length, (newCount) => {
  if (parentRef.value && systemStore.inspectorMode === 'TERMINAL') {
    const { scrollTop, scrollHeight, clientHeight } = parentRef.value
    const isAtBottom = scrollHeight - scrollTop - clientHeight < 50
    if (isAtBottom) {
      nextTick(() => {
        rowVirtualizer.value.scrollToIndex(newCount - 1)
      })
    }
  }
})

// Code inspection logic
watch(() => systemStore.inspectFile, (newPath) => {
  if (newPath) {
    eventBusService.send('READ_FILE', { path: newPath })
  }
})

watch([() => logStore.fileCache, () => systemStore.inspectFile, highlighter], () => {
  if (systemStore.inspectFile && highlighter.value) {
    const file = logStore.fileCache[systemStore.inspectFile]
    if (file) {
      highlightedCode.value = highlighter.value.codeToHtml(file.content, {
        lang: file.language || 'text',
        theme: 'github-dark'
      })
    }
  }
}, { deep: true })

// Diff inspection logic
watch([() => systemStore.inspectDiff, highlighter], () => {
  if (systemStore.inspectDiff && highlighter.value) {
    highlightedDiff.value = highlighter.value.codeToHtml(systemStore.inspectDiff, {
      lang: 'diff',
      theme: 'github-dark'
    })
  }
}, { immediate: true })

const currentFile = computed(() => {
  if (!systemStore.inspectFile) return null
  return logStore.fileCache[systemStore.inspectFile] || null
})

const copyCode = () => {
  if (systemStore.inspectorMode === 'CODE' && currentFile.value) {
    navigator.clipboard.writeText(currentFile.value.content)
  } else if (systemStore.inspectorMode === 'DIFF' && systemStore.inspectDiff) {
    navigator.clipboard.writeText(systemStore.inspectDiff)
  }
}
</script>

<template>
  <aside 
    v-if="systemStore.isInspectorOpen"
    class="w-[700px] bg-slate-900 border-l border-slate-800 flex flex-col h-full shadow-2xl animate-in slide-in-from-right duration-300 z-20"
  >
    <!-- Header -->
    <div class="p-4 border-b border-slate-800 flex justify-between items-center bg-slate-950">
      <div class="flex flex-col flex-1 min-w-0">
        <div class="flex items-center gap-3 mb-1">
          <h3 class="text-xs font-semibold text-slate-200 uppercase tracking-widest">Inspector</h3>
          <div class="flex bg-slate-900 rounded p-0.5 border border-slate-800">
            <button 
              @click="systemStore.inspectorMode = 'TERMINAL'"
              class="px-2 py-0.5 text-[10px] rounded transition-colors"
              :class="systemStore.inspectorMode === 'TERMINAL' ? 'bg-slate-700 text-white' : 'text-slate-500 hover:text-slate-300'"
            >
              Terminal
            </button>
            <button 
              @click="systemStore.inspectorMode = 'CODE'"
              class="px-2 py-0.5 text-[10px] rounded transition-colors"
              :class="systemStore.inspectorMode === 'CODE' ? 'bg-slate-700 text-white' : 'text-slate-500 hover:text-slate-300'"
            >
              Code
            </button>
            <button 
              @click="systemStore.inspectorMode = 'DIFF'"
              class="px-2 py-0.5 text-[10px] rounded transition-colors"
              :class="systemStore.inspectorMode === 'DIFF' ? 'bg-slate-700 text-white' : 'text-slate-500 hover:text-slate-300'"
            >
              Diff
            </button>
          </div>
        </div>
        <span class="text-[10px] font-mono text-slate-500 truncate block">
          <template v-if="systemStore.inspectorMode === 'TERMINAL'">Target: {{ systemStore.inspectorToolCallId }}</template>
          <template v-else-if="systemStore.inspectorMode === 'CODE'">File: {{ systemStore.inspectFile }}</template>
          <template v-else-if="systemStore.inspectorMode === 'DIFF'">Diff Review</template>
        </span>
      </div>
      
      <div class="flex items-center gap-2">
        <button 
          v-if="systemStore.inspectorMode === 'CODE' && systemStore.inspectFile"
          @click="systemStore.addContextToPrompt(systemStore.inspectFile!)"
          class="text-slate-500 hover:text-emerald-400 transition-colors p-1"
          title="Add to context"
        >
          <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 5v14M5 12h14"/></svg>
        </button>
        <button 
          v-if="(systemStore.inspectorMode === 'CODE' && currentFile) || (systemStore.inspectorMode === 'DIFF' && systemStore.inspectDiff)"
          @click="copyCode"
          class="text-slate-500 hover:text-emerald-400 transition-colors p-1"
          title="Copy to clipboard"
        >
          <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path></svg>
        </button>
        <button 
          @click="systemStore.closeInspector()"
          class="text-slate-500 hover:text-white transition-colors p-1"
        >
          <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg>
        </button>
      </div>
    </div>
    
    <!-- Content Area -->
    <div class="flex-1 relative bg-black overflow-hidden flex flex-col">
      
      <!-- Terminal View (Virtual List) -->
      <div 
        v-if="systemStore.inspectorMode === 'TERMINAL'"
        class="flex-1 flex flex-col min-h-0"
      >
        <!-- Search Bar -->
        <div class="px-4 py-2 border-b border-slate-800 bg-slate-900/50 flex items-center gap-3">
          <div class="relative flex-1">
            <span class="absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-500 text-[10px]">🔍</span>
            <input 
              v-model="systemStore.terminalSearchQuery"
              type="text"
              placeholder="Filter logs (regex supported)..."
              class="w-full bg-black border border-slate-800 rounded px-8 py-1 text-[11px] text-slate-300 focus:outline-none focus:border-emerald-500/50 placeholder:text-slate-600 transition-all"
            />
            <button 
              v-if="systemStore.terminalSearchQuery"
              @click="systemStore.terminalSearchQuery = ''"
              class="absolute right-2 top-1/2 -translate-y-1/2 text-slate-500 hover:text-slate-300 text-[10px]"
            >
              ✕
            </button>
          </div>
          <div class="text-[10px] text-slate-500 font-mono whitespace-nowrap">
            {{ ttyLines.length }} lines
          </div>
        </div>

        <div 
          ref="parentRef"
          class="flex-1 w-full overflow-auto p-4 font-mono text-[11px] leading-tight text-slate-300"
        >
          <div v-if="ttyLines.length === 0" class="opacity-30 italic p-4 text-center">
            {{ systemStore.terminalSearchQuery ? 'No lines matching search query.' : 'No output recorded for this tool call.' }}
          </div>
          
          <div
            v-else
            :style="{
              height: `${rowVirtualizer.getTotalSize()}px`,
              width: '100%',
              position: 'relative',
            }"
          >
            <div
              v-for="virtualRow in rowVirtualizer.getVirtualItems()"
              :key="virtualRow.index"
              :style="{
                position: 'absolute',
                top: 0,
                left: 0,
                width: '100%',
                height: `${virtualRow.size}px`,
                transform: `translateY(${virtualRow.start}px)`,
              }"
              class="whitespace-pre break-all border-b border-slate-900/30 py-0.5 px-1 hover:bg-slate-900/50"
            >
              {{ ttyLines[virtualRow.index] }}
            </div>
          </div>
        </div>
      </div>

      <!-- Code View -->
      <div v-else-if="systemStore.inspectorMode === 'CODE'" class="flex-1 flex flex-col min-h-0">
        <div v-if="!systemStore.inspectFile" class="flex-1 flex flex-col items-center justify-center p-8 text-center">
          <div class="text-3xl mb-4 opacity-20">📄</div>
          <h4 class="text-sm font-medium text-slate-400">No file selected</h4>
          <p class="text-[10px] text-slate-600 mt-2 max-w-xs leading-relaxed">
            Select a file path from the conversation or tool execution to inspect its content.
          </p>
        </div>
        <div v-else-if="!currentFile" class="flex-1 flex items-center justify-center">
          <div class="flex items-center gap-2 text-slate-500">
            <span class="w-4 h-4 border-2 border-slate-500 border-t-transparent rounded-full animate-spin"></span>
            <span class="text-xs">Reading file...</span>
          </div>
        </div>
        <div v-else class="flex-1 overflow-auto bg-[#0d1117]">
          <div v-html="highlightedCode" class="shiki-container"></div>
        </div>
      </div>
      
      <!-- Diff View -->
      <div v-else-if="systemStore.inspectorMode === 'DIFF'" class="flex-1 flex flex-col min-h-0">
        <div v-if="!systemStore.inspectDiff" class="flex-1 flex flex-col items-center justify-center p-8 text-center">
          <div class="text-3xl mb-4 opacity-20">📝</div>
          <h4 class="text-sm font-medium text-slate-400">No diff available</h4>
        </div>
        <div v-else class="flex-1 overflow-auto bg-[#0d1117]">
          <div v-html="highlightedDiff" class="shiki-container"></div>
        </div>
      </div>

    </div>
  </aside>
</template>

<style scoped>
.font-mono {
  font-family: 'JetBrains Mono', 'Fira Code', 'Ubuntu Mono', monospace;
}

/* Custom scrollbar */
::-webkit-scrollbar {
  width: 8px;
}
::-webkit-scrollbar-thumb {
  background: #334155;
}
::-webkit-scrollbar-track {
  background: #020617;
}

.shiki-container :deep(pre) {
  margin: 0;
  padding: 1.5rem;
  background-color: transparent !important;
  font-size: 12px;
  line-height: 1.6;
  tab-size: 4;
}

.shiki-container :deep(code) {
  font-family: inherit;
}
</style>
