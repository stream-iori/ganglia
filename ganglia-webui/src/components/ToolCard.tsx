import React, { useMemo, useState, useEffect, useCallback } from 'react';
import { useSystemStore } from '../stores/system';
import { useLogStore } from '../stores/log';
import type { ServerEvent, ToolStartData, ToolResultData } from '../types';
import { cn } from '../lib/utils';

interface ToolCardProps {
  event: ServerEvent<ToolStartData>;
  allEvents: ServerEvent[];
}

const ToolCard: React.FC<ToolCardProps> = ({ event, allEvents }) => {
  const systemStore = useSystemStore();
  const logStore = useLogStore();

  const resultEvent = useMemo(() => {
    return allEvents.find(
      (e) =>
        e.type === 'TOOL_RESULT' && (e.data as ToolResultData).toolCallId === event.data.toolCallId,
    ) as ServerEvent<ToolResultData> | undefined;
  }, [allEvents, event.data.toolCallId]);

  const isRunning = !resultEvent;
  const isError = resultEvent?.data.isError || (resultEvent?.data.exitCode !== 0 && !isRunning);

  const isRecovering = useMemo(() => {
    if (!isError) return false;

    const toolResults = allEvents.filter((e) => e.type === 'TOOL_RESULT');
    const lastToolResult = toolResults[toolResults.length - 1];

    if (
      lastToolResult &&
      (lastToolResult.data as ToolResultData).toolCallId === event.data.toolCallId
    ) {
      const resultIndex = allEvents.findIndex((e) => e.eventId === lastToolResult.eventId);
      const subsequentEvents = allEvents.slice(resultIndex + 1);
      const hasYielded = subsequentEvents.some(
        (e) => e.type === 'AGENT_MESSAGE' || e.type === 'ASK_USER' || e.type === 'SYSTEM_ERROR',
      );
      return !hasYielded;
    }
    return false;
  }, [allEvents, event.data.toolCallId, isError]);

  const toolName = event.data.toolName;
  const command = event.data.command;
  const summary = resultEvent?.data.summary || 'Executing...';

  const ttyLineCount = useMemo(() => {
    const lines = logStore.activeToolCalls[event.data.toolCallId];
    return lines ? lines.length : 0;
  }, [logStore.activeToolCalls, event.data.toolCallId]);

  const isMiniCommand = useMemo(() => {
    if (toolName === 'run_shell_command') {
      const cmd = command?.trim() || '';
      return /^(ls|pwd|whoami|cat|git status|git branch|echo|mkdir|rm)(\s|$)/.test(cmd);
    }
    return ['list_directory', 'read_file'].includes(toolName);
  }, [toolName, command]);

  const [isExpanded, setIsExpanded] = useState(!isMiniCommand);
  const [executionTime, setExecutionTime] = useState(0);

  useEffect(() => {
    let interval: ReturnType<typeof setInterval> | null = null;

    if (isRunning) {
      const start = Date.now();
      interval = setInterval(() => {
        setExecutionTime(Math.floor((Date.now() - start) / 1000));
      }, 1000);
    } else if (resultEvent && event.timestamp && resultEvent.timestamp) {
      setExecutionTime(Math.floor((resultEvent.timestamp - event.timestamp) / 1000));
    }

    return () => {
      if (interval) clearInterval(interval);
    };
  }, [isRunning, resultEvent, event.timestamp]);

  useEffect(() => {
    if (!isRunning && !isError) {
      setIsExpanded(false);
    }
  }, [isRunning, isError]);

  const handleCommandClick = useCallback(() => {
    const selection = window.getSelection()?.toString().trim();
    if (selection) {
      const pathRegex = /^([a-zA-Z0-9_\-.]+\/)*[a-zA-Z0-9_\-.]+\.[a-z0-9]+$/;
      if (pathRegex.test(selection)) {
        systemStore.toggleFileInspector(selection);
      }
    }
  }, [systemStore]);

  return (
    <div
      className={cn(
        'rounded-lg border bg-slate-900/50 overflow-hidden transition-all duration-300 cursor-pointer',
        !isError && !isRunning && 'border-slate-800',
        isRunning && 'border-amber-500/50 shadow-[0_0_15px_rgba(245,158,11,0.15)]',
        isError && 'border-rose-500/50 shadow-[0_0_15px_rgba(244,63,94,0.15)]',
        !isRunning && !isExpanded && 'opacity-70 hover:opacity-100',
      )}
      onClick={() => setIsExpanded(!isExpanded)}
    >
      <div
        className={cn(
          'px-4 py-2 flex items-center justify-between',
          isExpanded ? 'bg-slate-900 border-b border-slate-800/50' : 'bg-transparent',
        )}
      >
        <div className="flex items-center gap-3 overflow-hidden">
          {isRunning ? (
            <span className="w-2 h-2 bg-amber-500 rounded-full animate-pulse flex-shrink-0"></span>
          ) : isError ? (
            <span className="text-rose-500 text-xs font-bold flex-shrink-0">✕</span>
          ) : (
            <span className="text-emerald-500 text-xs font-bold flex-shrink-0">✓</span>
          )}

          <span className="text-[10px] font-mono font-bold uppercase tracking-wider text-slate-400 flex-shrink-0">
            {toolName}
          </span>

          {!isExpanded && (
            <span className="text-[10px] font-mono text-slate-500 truncate max-w-[300px] ml-2">
              {command}
            </span>
          )}
        </div>

        <div className="flex gap-3 items-center flex-shrink-0">
          {['replace', 'write_file'].includes(toolName) && resultEvent && !isError && (
            <button
              onClick={(e) => {
                e.stopPropagation();
                systemStore.toggleDiffInspector(resultEvent.data.fullOutput || summary);
              }}
              className="flex items-center gap-1 px-2 py-0.5 rounded bg-emerald-900/30 border border-emerald-800/50 text-emerald-400 hover:bg-emerald-900/50 transition-colors"
              title="Review Changes"
            >
              <span className="text-[10px]">👁️</span>
              <span className="text-[9px] font-bold uppercase tracking-wider">Diff</span>
            </button>
          )}

          {(executionTime >= 0 || isRunning) && (
            <span
              className={cn(
                'text-[9px] font-mono',
                isRunning ? 'text-amber-500 font-bold' : 'text-slate-600',
              )}
            >
              {isRunning ? 'running ' : ''}
              {executionTime === 0 && !isRunning ? '< 1s' : `${executionTime}s`}
            </span>
          )}
          {ttyLineCount > 0 && (
            <span className="text-[9px] font-mono text-slate-500 bg-slate-950 px-1.5 py-0.5 rounded border border-slate-800">
              {ttyLineCount} lines
            </span>
          )}
          {isExpanded && (
            <button
              onClick={(e) => {
                e.stopPropagation();
                systemStore.toggleInspector(event.data.toolCallId, 'TERMINAL');
              }}
              className="text-[10px] text-slate-500 hover:text-slate-300 underline font-mono transition-colors"
            >
              Logs
            </button>
          )}
        </div>
      </div>

      {isExpanded && (
        <div className="px-4 py-3" onClick={(e) => e.stopPropagation()}>
          <div
            onClick={handleCommandClick}
            className="text-xs font-mono text-slate-300 bg-slate-950 p-2.5 rounded mb-2 border border-slate-800/50 overflow-x-auto whitespace-nowrap text-wrap cursor-text group"
            title="Double-click a path to inspect"
          >
            <span className="text-slate-600 mr-2 select-none">$</span>
            {command}
          </div>

          <div className="text-[11px] text-slate-400 leading-relaxed max-h-[200px] overflow-y-auto scrollbar-thin scrollbar-thumb-slate-800">
            {summary}
          </div>

          {isRecovering && (
            <div className="mt-3 flex items-center gap-2 text-rose-400 bg-rose-950/30 p-2 rounded border border-rose-900/50">
              <span className="w-1.5 h-1.5 bg-rose-500 rounded-full animate-pulse"></span>
              <span className="text-[10px] font-mono">Agent is attempting to recover...</span>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default ToolCard;
