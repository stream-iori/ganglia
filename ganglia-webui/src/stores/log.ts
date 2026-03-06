import { defineStore } from 'pinia'
import type { ServerEvent, ToolStartData, FileContentData, FileTreeNode, TokenData } from '../types'

export const useLogStore = defineStore('log', {
  state: () => ({
    events: [] as ServerEvent[],
    // Map to track active tool calls for TTY streaming
    activeToolCalls: {} as Record<string, string[]>,
    // Cache for file contents
    fileCache: {} as Record<string, FileContentData>,
    // Project file tree
    fileTree: null as FileTreeNode | null,
    // Live streaming content
    streamingMessage: ''
  }),
  actions: {
    addEvent(event: ServerEvent) {
      if (event.type === 'TOKEN') {
        const data = event.data as TokenData
        this.streamingMessage += data.content
        return // Do NOT push tokens to the events array
      }

      // If we receive a non-TOKEN event, we should probably clear the streaming message
      // as it means the turn or a phase has finished and the full content will be in the next event.
      if (event.type !== 'TOOL_OUTPUT_STREAM') {
        this.streamingMessage = ''
      }

      // --- De-duplication and Placeholder handling for non-streaming mode ---
      if (this.events.length > 0) {
        const lastIndex = this.events.length - 1;
        const lastEvent = this.events[lastIndex];

        // 1. If we receive a real THOUGHT/AGENT_MESSAGE, remove the "..." placeholder
        if ((event.type === 'THOUGHT' || event.type === 'AGENT_MESSAGE' || event.type === 'TOOL_START') && 
             lastEvent && lastEvent.type === 'THOUGHT' && lastEvent.data.content === '...') {
          this.events.splice(lastIndex, 1);
        }

        // 2. Refresh last event after potential removal
        const currentLastEvent = this.events.length > 0 ? this.events[this.events.length - 1] : null;

        // 3. Avoid double rendering if THOUGHT and AGENT_MESSAGE have same content
        if (event.type === 'AGENT_MESSAGE' && currentLastEvent && 
            currentLastEvent.type === 'THOUGHT' && 
            currentLastEvent.data.content.trim() === event.data.content.trim()) {
          // Replace THOUGHT with AGENT_MESSAGE to give it more prominence
          this.events.splice(this.events.length - 1, 1, event);
          return;
        }
      }

      this.events.push(event)
      
      // Handle TTY initialization if it's a TOOL_START
      if (event.type === 'TOOL_START') {
        const data = event.data as ToolStartData
        if (data.toolCallId && !this.activeToolCalls[data.toolCallId]) {
          this.activeToolCalls[data.toolCallId] = []
        }
      }

      if (event.type === 'FILE_CONTENT') {
        const data = event.data as FileContentData
        this.fileCache[data.path] = data
      }

      if (event.type === 'FILE_TREE') {
        this.fileTree = event.data as FileTreeNode
      }
    },
    appendTty(toolCallId: string, text: string) {
      if (this.activeToolCalls[toolCallId]) {
        this.activeToolCalls[toolCallId].push(text)
      }
    },
    clear() {
      this.events = []
      this.activeToolCalls = {}
      this.fileCache = {}
      this.fileTree = null
      this.streamingMessage = ''
    }
  }
})
