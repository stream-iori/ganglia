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
    streamingMessage: '',
    streamingThought: ''
  }),
  actions: {
    addEvent(event: ServerEvent) {
      if (event.type === 'TOKEN') {
        const data = event.data as TokenData
        
        // Infer role if not provided
        let role = data.role
        if (!role) {
          const lastEvent = this.events.length > 0 ? this.events[this.events.length - 1] : null
          if (lastEvent && lastEvent.type === 'THOUGHT' && lastEvent.data.content === '...') {
            role = 'thought'
          } else {
            role = 'answer'
          }
        }

        if (role === 'thought') {
          this.streamingThought += data.content
        } else {
          this.streamingMessage += data.content
        }
        return // Do NOT push tokens to the events array
      }

      // If we receive a non-TOKEN event, we clear the corresponding streaming buffer
      if (event.type === 'THOUGHT') {
        this.streamingThought = ''
      } else if (event.type === 'AGENT_MESSAGE') {
        this.streamingMessage = ''
      } else if (event.type !== 'TOOL_OUTPUT_STREAM') {
        // Clear all if it's a completely different phase (e.g. tool start)
        this.streamingMessage = ''
        this.streamingThought = ''
      }

      // --- 0. Update existing event if same eventId arrives ---
      const existingIdx = this.events.findIndex(e => e.eventId === event.eventId)
      if (existingIdx !== -1) {
        this.events[existingIdx] = event
        return
      }

      // --- 1. Robust Placeholder handling ---
      const isThoughtPlaceholder = (e: ServerEvent) => e && e.type === 'THOUGHT' && String(e.data.content).trim() === '...';
      
      if (event.type === 'THOUGHT' || event.type === 'AGENT_MESSAGE' || event.type === 'TOOL_START') {
        // Find "..." placeholder in last few events
        const placeholderIdx = this.events.findLastIndex(isThoughtPlaceholder);
        
        if (placeholderIdx !== -1 && placeholderIdx >= this.events.length - 3) {
          if (event.type === 'THOUGHT' && !isThoughtPlaceholder(event)) {
            // Replace placeholder with real thought content to maintain position
            this.events[placeholderIdx] = event;
            return;
          } else if (event.type !== 'THOUGHT') {
            // Remove placeholder if we are moving to next phase (Message/Tool)
            this.events.splice(placeholderIdx, 1);
          }
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
