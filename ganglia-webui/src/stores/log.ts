import { create } from 'zustand'
import type { ServerEvent, ToolStartData, FileContentData, FileTreeNode, TokenData } from '../types'

interface LogState {
  events: ServerEvent[]
  activeToolCalls: Record<string, string[]>
  fileCache: Record<string, FileContentData>
  fileTree: FileTreeNode | null
  streamingMessage: string
  streamingThought: string

  addEvent: (event: ServerEvent) => void
  appendTty: (toolCallId: string, text: string) => void
  clear: () => void
}

export const useLogStore = create<LogState>((set) => ({
  events: [],
  activeToolCalls: {},
  fileCache: {},
  fileTree: null,
  streamingMessage: '',
  streamingThought: '',

  addEvent: (event) => {
    set((state) => {
      const newState = { ...state }

      if (event.type === 'TOKEN') {
        const data = event.data as TokenData
        let role = data.role
        if (!role) {
          const lastEvent = state.events.length > 0 ? state.events[state.events.length - 1] : null
          if (lastEvent && lastEvent.type === 'THOUGHT' && (lastEvent.data as any).content === '...') {
            role = 'thought'
          } else {
            role = 'answer'
          }
        }

        if (role === 'thought') {
          newState.streamingThought += data.content
        } else {
          newState.streamingMessage += data.content
        }
        return newState
      }

      if (event.type === 'THOUGHT') {
        newState.streamingThought = ''
      } else if (event.type === 'AGENT_MESSAGE') {
        newState.streamingMessage = ''
      } else if (event.type !== 'TOOL_OUTPUT_STREAM') {
        newState.streamingMessage = ''
        newState.streamingThought = ''
      }

      const newEvents = [...state.events]
      const existingIdx = newEvents.findIndex(e => e.eventId === event.eventId)
      if (existingIdx !== -1) {
        newEvents[existingIdx] = event
        newState.events = newEvents
        return newState
      }

      const isThoughtPlaceholder = (e: ServerEvent) => e && e.type === 'THOUGHT' && String((e.data as any).content).trim() === '...'
      
      if (event.type === 'THOUGHT' || event.type === 'AGENT_MESSAGE' || event.type === 'TOOL_START') {
        // Find existing thought/message with same content to deduplicate
        if (event.type === 'AGENT_MESSAGE' || event.type === 'THOUGHT') {
          const content = (event.data as any).content
          const sameContentIdx = newEvents.findLastIndex((e: ServerEvent) => (e.type === 'THOUGHT' || e.type === 'AGENT_MESSAGE') && (e.data as any).content === content)
          if (sameContentIdx !== -1) {
            // Keep the AGENT_MESSAGE version if possible
            if (event.type === 'AGENT_MESSAGE') {
              newEvents[sameContentIdx] = event
            }
            newState.events = newEvents
            return newState
          }
        }

        const placeholderIdx = newEvents.findLastIndex(isThoughtPlaceholder)
        
        if (placeholderIdx !== -1 && placeholderIdx >= newEvents.length - 3) {
          if (event.type === 'THOUGHT' && !isThoughtPlaceholder(event)) {
            newEvents[placeholderIdx] = event
            newState.events = newEvents
            return newState
          } else if (event.type !== 'THOUGHT') {
            newEvents.splice(placeholderIdx, 1)
          }
        }
      }

      newEvents.push(event)
      newState.events = newEvents
      
      if (event.type === 'TOOL_START') {
        const data = event.data as ToolStartData
        if (data.toolCallId && !newState.activeToolCalls[data.toolCallId]) {
          newState.activeToolCalls = { ...newState.activeToolCalls, [data.toolCallId]: [] }
        }
      }

      if (event.type === 'FILE_CONTENT') {
        const data = event.data as FileContentData
        newState.fileCache = { ...newState.fileCache, [data.path]: data }
      }

      if (event.type === 'FILE_TREE') {
        newState.fileTree = event.data as FileTreeNode
      }

      return newState
    })
  },

  appendTty: (toolCallId, text) => {
    set((state) => {
      if (state.activeToolCalls[toolCallId]) {
        return {
          activeToolCalls: {
            ...state.activeToolCalls,
            [toolCallId]: [...state.activeToolCalls[toolCallId], text]
          }
        }
      }
      return state
    })
  },

  clear: () => set({
    events: [],
    activeToolCalls: {},
    fileCache: {},
    fileTree: null,
    streamingMessage: '',
    streamingThought: ''
  })
}))
