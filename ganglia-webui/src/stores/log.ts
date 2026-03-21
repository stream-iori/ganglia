import { create } from 'zustand';
import type {
  ServerEvent,
  ToolStartData,
  FileContentData,
  FileTreeNode,
  TokenData,
} from '../types';

interface LogState {
  events: ServerEvent[];
  activeToolCalls: Record<string, string[]>;
  fileCache: Record<string, FileContentData>;
  fileTree: FileTreeNode | null;
  streamingMessage: string;
  streamingThought: string;
  thoughtStartTime: number | null;

  addEvent: (event: ServerEvent) => void;
  appendTty: (toolCallId: string, text: string) => void;
  clear: () => void;
}

export const useLogStore = create<LogState>((set) => ({
  events: [],
  activeToolCalls: {},
  fileCache: {},
  fileTree: null,
  streamingMessage: '',
  streamingThought: '',
  thoughtStartTime: null,

  addEvent: (event) => {
    set((state) => {
      const newState = { ...state };
      const newEvents = [...state.events];

      if (event.type === 'TOKEN') {
        const data = event.data as TokenData;
        let role = data.role;
        if (!role) {
          const lastEvent = state.events.length > 0 ? state.events[state.events.length - 1] : null;
          if (
            lastEvent &&
            lastEvent.type === 'THOUGHT' &&
            (lastEvent.data as unknown).content === '...'
          ) {
            role = 'thought';
          } else {
            role = 'answer';
          }
        }

        if (role === 'thought') {
          if (newState.streamingThought === '' && !newState.thoughtStartTime) {
            newState.thoughtStartTime = Date.now();
          }
          newState.streamingThought += data.content;
        } else {
          newState.streamingMessage += data.content;
        }
        return newState;
      }

      if (event.type === 'THOUGHT') {
        if (!newState.thoughtStartTime) newState.thoughtStartTime = Date.now();
        if ((event.data as unknown).content !== '...') {
          newState.streamingThought = '';
        }
      } else if (
        event.type === 'AGENT_MESSAGE' ||
        event.type === 'TOOL_START' ||
        event.type === 'ASK_USER' ||
        event.type === 'USER_MESSAGE'
      ) {
        if (event.type === 'AGENT_MESSAGE') newState.streamingMessage = '';

        if (newState.thoughtStartTime) {
          const lastThoughtIdx = newEvents.findLastIndex((e) => e.type === 'THOUGHT');
          if (lastThoughtIdx !== -1) {
            const lastThought = {
              ...newEvents[lastThoughtIdx],
              data: { ...newEvents[lastThoughtIdx].data },
            };
            if (!(lastThought.data as unknown).durationMs) {
              (lastThought.data as unknown).durationMs = Date.now() - newState.thoughtStartTime;
              newEvents[lastThoughtIdx] = lastThought;
            }
          }
          newState.thoughtStartTime = null;
        }
      } else if (event.type !== 'TOOL_OUTPUT_STREAM' && event.type !== 'PLAN_UPDATED') {
        newState.streamingMessage = '';
        newState.streamingThought = '';
        newState.thoughtStartTime = null;
      }

      const existingIdx = newEvents.findIndex((e) => e.eventId === event.eventId);
      if (existingIdx !== -1) {
        newEvents[existingIdx] = event;
        newState.events = newEvents;
        return newState;
      }

      const isThoughtPlaceholder = (e: ServerEvent) =>
        e && e.type === 'THOUGHT' && String((e.data as unknown).content).trim() === '...';

      // We only want to replace the `...` placeholder with the real THOUGHT event.
      // We DO NOT want to delete a real THOUGHT event just because an AGENT_MESSAGE arrived.

      if (
        event.type === 'THOUGHT' ||
        event.type === 'AGENT_MESSAGE' ||
        event.type === 'TOOL_START'
      ) {
        const placeholderIdx = newEvents.findLastIndex(isThoughtPlaceholder);

        if (placeholderIdx !== -1) {
          if (event.type === 'THOUGHT') {
            // Replace placeholder with the actual thought
            newEvents[placeholderIdx] = event;
            newState.events = newEvents;
            return newState;
          } else {
            // Something else started, remove the thought placeholder
            newEvents.splice(placeholderIdx, 1);
          }
        }

        // Deduplication for exact same content between THOUGHT and AGENT_MESSAGE (a backend quirk)
        if (event.type === 'AGENT_MESSAGE') {
          const content = (event.data as unknown).content;
          const sameContentIdx = newEvents.findLastIndex(
            (e: ServerEvent) => e.type === 'THOUGHT' && (e.data as unknown).content === content,
          );
          if (sameContentIdx !== -1) {
            // Upgrade THOUGHT to AGENT_MESSAGE if identical
            newEvents[sameContentIdx] = event;
            newState.events = newEvents;
            return newState;
          }
        }
      }

      newEvents.push(event);
      newState.events = newEvents;

      if (event.type === 'TOOL_START') {
        const data = event.data as ToolStartData;
        if (data.toolCallId && !newState.activeToolCalls[data.toolCallId]) {
          newState.activeToolCalls = { ...newState.activeToolCalls, [data.toolCallId]: [] };
        }
      }

      if (event.type === 'FILE_CONTENT') {
        const data = event.data as FileContentData;
        newState.fileCache = { ...newState.fileCache, [data.path]: data };
      }

      if (event.type === 'FILE_TREE') {
        newState.fileTree = event.data as FileTreeNode;
      }

      return newState;
    });
  },

  appendTty: (toolCallId, text) => {
    set((state) => {
      if (state.activeToolCalls[toolCallId]) {
        return {
          activeToolCalls: {
            ...state.activeToolCalls,
            [toolCallId]: [...state.activeToolCalls[toolCallId], text],
          },
        };
      }
      return state;
    });
  },

  clear: () =>
    set({
      events: [],
      activeToolCalls: {},
      fileCache: {},
      fileTree: null,
      streamingMessage: '',
      streamingThought: '',
      thoughtStartTime: null,
    }),
}));
