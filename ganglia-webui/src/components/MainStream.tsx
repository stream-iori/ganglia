import React, { useRef, useState, useEffect, useMemo, useCallback } from 'react';
import { eventBusService } from '../services/eventbus';
import { useLogStore } from '../stores/log';
import { useSystemStore } from '../stores/system';
import type {
  ServerEvent,
  ToolResultData,
  ToolStartData,
  UserMessageData,
  ThoughtData,
  AgentMessageData,
  SystemErrorData,
  AskUserData,
} from '../types';
import ThoughtCard from './ThoughtCard';
import ToolCard from './ToolCard';
import AgentMessage from './AgentMessage';
import AskUserForm from './AskUserForm';
import TaskReviewCard from './TaskReviewCard';
import StatusBar from './StatusBar';
import { cn } from '../lib/utils';

const MainStream: React.FC = () => {
  const logStore = useLogStore();
  const systemStore = useSystemStore();
  const [prompt, setPrompt] = useState('');
  const streamEndRef = useRef<HTMLDivElement>(null);
  const streamContainerRef = useRef<HTMLDivElement>(null);

  const [isScrolledToBottom, setIsScrolledToBottom] = useState(true);
  const [hasNewContent, setHasNewContent] = useState(false);
  const lastEventCountRef = useRef(logStore.events.length);
  const lastStreamingMsgRef = useRef(logStore.streamingMessage);

  const handleScroll = useCallback((e: React.UIEvent<HTMLDivElement>) => {
    const target = e.currentTarget;
    const atBottom = Math.abs(target.scrollHeight - target.clientHeight - target.scrollTop) < 50;
    setIsScrolledToBottom(atBottom);
    if (atBottom) {
      setHasNewContent(false);
    }
  }, []);

  const scrollToBottom = useCallback(() => {
    setIsScrolledToBottom(true);
    setHasNewContent(false);
    streamEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, []);

  useEffect(() => {
    const eventsChanged = logStore.events.length > lastEventCountRef.current;
    const streamingChanged =
      logStore.streamingMessage !== lastStreamingMsgRef.current && !!logStore.streamingMessage;

    if (eventsChanged || streamingChanged) {
      if (!isScrolledToBottom) {
        setHasNewContent(true);
      } else {
        scrollToBottom();
      }
    }

    lastEventCountRef.current = logStore.events.length;
    lastStreamingMsgRef.current = logStore.streamingMessage;
  }, [logStore.events.length, logStore.streamingMessage, isScrolledToBottom, scrollToBottom]);

  useEffect(() => {
    if (systemStore.pendingContextPath) {
      const mention = `@${systemStore.pendingContextPath} `;
      setPrompt((prev) =>
        !prev.includes(mention) ? (prev.trim() ? prev.trim() + ' ' : '') + mention : prev,
      );
      systemStore.clearPendingContext();
    }
  }, [systemStore]);

  const isBlocked = useMemo(
    () => !!systemStore.activeAskId || isSending,
    [systemStore.activeAskId, isSending],
  );

  const typingStatus = useMemo(() => {
    const lastEvent = logStore.events[logStore.events.length - 1];
    if (
      lastEvent?.type === 'TOOL_START' &&
      !logStore.events.find(
        (e) =>
          e.type === 'TOOL_RESULT' &&
          (e.data as ToolResultData).toolCallId === (lastEvent.data as ToolStartData).toolCallId,
      )
    ) {
      const name = (lastEvent.data as ToolStartData).toolName;
      if (['read_file', 'list_directory'].includes(name)) return 'Agent is reading files...';
      if (name === 'run_shell_command') return 'Agent is executing a command...';
      if (['write_file', 'replace'].includes(name)) return 'Agent is writing code...';
      return `Agent is using ${name}...`;
    }

    if (logStore.streamingThought) return 'Agent is thinking...';
    if (logStore.streamingMessage) return 'Agent is responding...';

    return 'Agent is thinking...';
  }, [logStore.events, logStore.streamingThought, logStore.streamingMessage]);

  const sendMessage = useCallback(() => {
    if (!prompt.trim() || isBlocked) return;
    setIsSending(true);
    eventBusService.send('START', { prompt }).finally(() => setIsSending(false));
    setPrompt('');
    setIsScrolledToBottom(true);
    scrollToBottom();
  }, [prompt, isBlocked, scrollToBottom]);

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      sendMessage();
    }
  };

  const retry = () => {
    eventBusService.send('RETRY', {});
  };

  const renderEventTimeline = () => {
    const visibleEvents = logStore.events.filter((e) => {
      if (
        ![
          'USER_MESSAGE',
          'THOUGHT',
          'TOOL_START',
          'AGENT_MESSAGE',
          'ASK_USER',
          'SYSTEM_ERROR',
        ].includes(e.type)
      )
        return false;
      if (
        e.type === 'THOUGHT' &&
        (e.data as ThoughtData).content === '...' &&
        logStore.streamingThought
      )
        return false;
      return true;
    });

    return visibleEvents.map((event, index) => {
      // User messages stand out from the timeline
      if (event.type === 'USER_MESSAGE') {
        return (
          <div key={event.eventId} className="flex justify-end my-4">
            <div className="bg-slate-800 text-slate-100 px-4 py-2 rounded-2xl rounded-tr-none max-w-[80%] shadow-md border border-slate-700/50">
              {(event.data as UserMessageData).content}
            </div>
          </div>
        );
      }

      const isLastTimelineItem =
        index === visibleEvents.length - 1 &&
        !logStore.streamingThought &&
        !logStore.streamingMessage;

      // General Agent Event Wrapper with Timeline Line
      return (
        <div key={event.eventId} className="relative pl-6 py-2 group">
          {/* Vertical timeline line */}
          <div
            className={cn(
              'absolute left-[11px] top-0 w-0.5 bg-slate-800/50',
              isLastTimelineItem ? 'h-6' : 'h-full',
            )}
          ></div>

          {/* Timeline node */}
          <div className="absolute left-[9px] top-4 w-1.5 h-1.5 rounded-full bg-slate-600 ring-4 ring-slate-950 group-hover:bg-slate-400 transition-colors"></div>

          {/* Event Content */}
          <div className="relative -mt-1.5">
            {event.type === 'THOUGHT' &&
              (() => {
                const thoughtData = event.data as ThoughtData;
                const content = thoughtData.content;
                const durationMs = (thoughtData as ThoughtData).durationMs;
                if (content === '...' && logStore.streamingThought) return null;
                return <ThoughtCard content={content} durationMs={durationMs} />;
              })()}

            {event.type === 'TOOL_START' && (
              <div className="ml-2">
                <ToolCard event={event as ServerEvent<ToolStartData>} allEvents={logStore.events} />
              </div>
            )}

            {event.type === 'AGENT_MESSAGE' &&
              (() => {
                const content = (event.data as AgentMessageData).content;
                return (
                  <div className="ml-2 mt-2">
                    <AgentMessage content={content} />
                    {content.includes('Task completed') && <TaskReviewCard />}
                  </div>
                );
              })()}

            {event.type === 'ASK_USER' && (
              <div className="ml-2">
                <AskUserForm event={event as ServerEvent<AskUserData>} />
              </div>
            )}

            {event.type === 'SYSTEM_ERROR' &&
              (() => {
                const data = event.data as SystemErrorData;
                return (
                  <div className="ml-2 bg-rose-950/20 border border-rose-500/50 p-4 rounded-lg text-rose-200 text-xs shadow-lg mt-2">
                    <div className="font-bold mb-1 uppercase tracking-tight flex items-center justify-between">
                      <span>System Error: {data.code}</span>
                      {data.canRetry && (
                        <button
                          onClick={retry}
                          className="bg-rose-500 hover:bg-rose-400 text-white px-2 py-0.5 rounded text-[10px] transition-colors"
                        >
                          Retry Last Command
                        </button>
                      )}
                    </div>
                    {data.message}
                  </div>
                );
              })()}
          </div>
        </div>
      );
    });
  };

  return (
    <main className="flex-1 flex flex-col bg-slate-950 h-full relative overflow-hidden">
      <StatusBar />

      <div
        className="flex-1 overflow-y-auto p-6 scrollbar-thin scrollbar-thumb-slate-800 relative"
        ref={streamContainerRef}
        onScroll={handleScroll}
      >
        {isBlocked && (
          <div className="absolute inset-0 bg-slate-950/40 backdrop-blur-[1px] z-10 transition-all duration-500"></div>
        )}

        <div className="max-w-3xl mx-auto pb-20 relative z-20">
          {logStore.events.length === 0 && (
            <div className="text-slate-500 text-center py-20">
              <div className="text-4xl mb-4 opacity-20 text-emerald-500">⚛</div>
              <p className="text-sm font-medium text-slate-300">Ganglia TUI Pro</p>
              <p className="text-[10px] opacity-40 mt-1">Autonomous Coding Agent Navigator</p>
            </div>
          )}

          {/* Render historical timeline */}
          {renderEventTimeline()}

          {/* Render active streaming states attached to the timeline */}
          {(logStore.streamingThought || logStore.streamingMessage) && (
            <div className="relative pl-6 py-2">
              {/* Timeline extending to current stream */}
              <div className="absolute left-[11px] top-0 w-0.5 h-full bg-gradient-to-b from-slate-800/50 to-transparent"></div>

              <div className="relative -mt-1.5 ml-2">
                {logStore.streamingThought && (
                  <div className="animate-in fade-in duration-300">
                    <div className="flex items-center gap-2 mb-2 text-slate-500">
                      <span className="w-1.5 h-1.5 bg-amber-500 rounded-full animate-pulse"></span>
                      <span className="text-[10px] font-bold uppercase tracking-widest">
                        Agent is thinking...
                      </span>
                    </div>
                    <ThoughtCard content={logStore.streamingThought} initiallyExpanded={true} />
                  </div>
                )}

                {logStore.streamingMessage && (
                  <div className="animate-in fade-in duration-300 mt-4">
                    <div className="flex items-center gap-2 mb-2 text-slate-500">
                      <span className="w-1.5 h-1.5 bg-emerald-500 rounded-full animate-pulse"></span>
                      <span className="text-[10px] font-bold uppercase tracking-widest">
                        {typingStatus}
                      </span>
                    </div>
                    <AgentMessage content={logStore.streamingMessage} />
                  </div>
                )}
              </div>
            </div>
          )}

          <div ref={streamEndRef} className="h-1"></div>
        </div>
      </div>

      <div className="p-6 bg-gradient-to-t from-slate-950 via-slate-950/95 to-transparent border-t border-slate-900/50 relative">
        <div
          className={cn(
            'transition-all duration-300 ease-out absolute -top-12 left-1/2 -translate-x-1/2 z-30',
            hasNewContent
              ? 'transform translate-y-0 opacity-100'
              : 'transform translate-y-4 opacity-0 pointer-events-none',
          )}
        >
          <button
            onClick={scrollToBottom}
            className="flex items-center gap-2 px-4 py-2 bg-emerald-600 hover:bg-emerald-500 text-white rounded-full shadow-2xl shadow-emerald-900/20 text-xs font-bold transition-all"
          >
            <svg
              xmlns="http://www.w3.org/2000/svg"
              width="14"
              height="14"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="3"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <path d="M12 5v14M5 12l7 7 7-7" />
            </svg>
            New Messages
            <span className="w-2 h-2 bg-white rounded-full animate-ping"></span>
          </button>
        </div>

        <div className="max-w-3xl mx-auto relative group">
          <textarea
            value={prompt}
            onChange={(e) => setPrompt(e.target.value)}
            onKeyDown={handleKeyDown}
            disabled={isBlocked}
            placeholder={
              isBlocked ? 'Decision required above...' : 'Type a command or ask a question...'
            }
            className="w-full bg-slate-900/80 border border-slate-800 rounded-xl pl-4 pr-24 py-4 text-slate-200 focus:outline-none focus:border-emerald-500/50 focus:bg-slate-900 transition-all resize-none h-20 shadow-2xl disabled:opacity-50 disabled:cursor-not-allowed"
          />

          <div className="absolute right-3 top-1/2 -translate-y-1/2 flex items-center gap-2">
            {!isBlocked && (
              <kbd className="text-[10px] text-slate-600 bg-slate-950 px-1.5 py-0.5 rounded border border-slate-800 font-mono text-xs">
                Enter
              </kbd>
            )}
            <button
              onClick={sendMessage}
              disabled={isBlocked}
              className="bg-emerald-600 hover:bg-emerald-500 text-white p-2 rounded-lg transition-all active:scale-95 shadow-lg disabled:bg-slate-800 disabled:text-slate-600"
            >
              <svg
                xmlns="http://www.w3.org/2000/svg"
                width="18"
                height="18"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
              >
                <line x1="22" y1="2" x2="11" y2="13"></line>
                <polygon points="22 2 15 22 11 13 2 9 22 2"></polygon>
              </svg>
            </button>
          </div>
        </div>
      </div>
    </main>
  );
};

export default MainStream;
