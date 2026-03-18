import React, { useEffect, useState, useMemo } from 'react'
import { useSystemStore, AgentPhase } from '../stores/system'
import { useLogStore } from '../stores/log'
import { eventBusService } from '../services/eventbus'
import { cn } from '../lib/utils'

const StatusBar: React.FC = () => {
  const systemStore = useSystemStore()
  const logStore = useLogStore()
  const [showFileSyncTip, setShowFileSyncTip] = useState(false)

  const isAgentBusy = useMemo(() => {
    if (logStore.streamingMessage) return true
    const toolStarts = logStore.events.filter((e) => e.type === 'TOOL_START')
    const toolResults = logStore.events.filter((e) => e.type === 'TOOL_RESULT')
    return toolStarts.length > toolResults.length
  }, [logStore.streamingMessage, logStore.events])

  useEffect(() => {
    const events = logStore.events
    if (events.length === 0) {
      if (systemStore.currentPhase !== 'IDLE') systemStore.setPhase('IDLE')
      return
    }

    const lastEvent = events[events.length - 1]
    let newPhase: AgentPhase = 'IDLE'

    if (lastEvent.type === 'ASK_USER') {
      newPhase = 'WAITING'
    } else if (lastEvent.type === 'TOOL_START' || isAgentBusy) {
      newPhase = 'EXECUTING'
    } else if (lastEvent.type === 'THOUGHT') {
      newPhase = 'PLANNING'
    } else if (lastEvent.type === 'AGENT_MESSAGE' && (lastEvent.data.content || '').includes('Task completed')) {
      newPhase = 'REVIEWING'
    }

    if (systemStore.currentPhase !== newPhase) {
      systemStore.setPhase(newPhase)
    }
  }, [logStore.events, isAgentBusy, systemStore.currentPhase, systemStore.setPhase])

  useEffect(() => {
    if (systemStore.fileTreeUpdatedAt > 0) {
      setShowFileSyncTip(true)
      const timer = setTimeout(() => setShowFileSyncTip(false), 5000)
      return () => clearTimeout(timer)
    }
  }, [systemStore.fileTreeUpdatedAt])

  const stopAgent = () => {
    eventBusService.send('CANCEL', {})
    logStore.addEvent({
      type: 'SYSTEM_ERROR',
      eventId: 'cancel-' + Date.now(),
      timestamp: Date.now(),
      data: {
        code: 'USER_CANCELLED',
        message: 'Task was forcibly stopped by the user.',
        canRetry: false,
      },
    })
  }

  const formatPath = (path: string) => {
    if (!path || path === 'Loading...') return path
    const parts = path.split('/')
    if (parts.length > 3) {
      return '.../' + parts.slice(-3).join('/')
    }
    return path
  }

  return (
    <>
      {systemStore.status !== 'CONNECTED' && (
        <div className="bg-amber-500/10 border-b border-amber-500/20 px-4 py-1.5 flex items-center justify-center gap-2 animate-pulse">
          <span className="w-1.5 h-1.5 bg-amber-500 rounded-full"></span>
          <span className="text-[10px] font-bold text-amber-500 uppercase tracking-tighter">
            {systemStore.status === 'RECONNECTING' ? 'Reconnecting to Ganglia Core...' : 'Disconnected'}
          </span>
        </div>
      )}

      <div className="h-12 border-b border-slate-900 bg-slate-950/50 backdrop-blur flex items-center justify-between px-6 sticky top-0 z-10 shrink-0">
        <div className="flex items-center gap-6">
          <div className="flex items-center gap-2">
            <div
              className={cn(
                'w-2 h-2 rounded-full',
                systemStore.status === 'CONNECTED' ? 'bg-emerald-500 shadow-[0_0_8px_rgba(16,185,129,0.5)]' : 'bg-slate-700'
              )}
            ></div>
            <span className="text-[10px] font-mono text-slate-500 uppercase tracking-widest">Core Status</span>
          </div>

          <div className="flex items-center gap-1.5 border-l border-slate-800 pl-6 py-1">
            <span className="text-[10px] font-mono text-slate-500 uppercase tracking-widest">Phase</span>
            <span
              className={cn(
                'text-[10px] font-bold px-2 py-0.5 rounded border transition-colors',
                systemStore.currentPhase === 'IDLE' && 'bg-slate-800 text-slate-400 border-slate-700',
                systemStore.currentPhase === 'PLANNING' && 'bg-blue-900/30 text-blue-400 border-blue-800/50',
                systemStore.currentPhase === 'EXECUTING' && 'bg-amber-900/30 text-amber-400 border-amber-800/50',
                systemStore.currentPhase === 'WAITING' && 'bg-purple-900/30 text-purple-400 border-purple-800/50',
                systemStore.currentPhase === 'REVIEWING' && 'bg-emerald-900/30 text-emerald-400 border-emerald-800/50'
              )}
            >
              {systemStore.currentPhase}
            </span>
          </div>

          <div className="flex items-center gap-2 border-l border-slate-800 pl-6 py-1 max-w-[300px]" title="Current Workspace">
            <span className="text-[10px] font-mono text-slate-500 uppercase tracking-widest">Workspace</span>
            <span className="text-[11px] font-mono text-slate-300 truncate bg-slate-900 px-2 py-0.5 rounded border border-slate-800">
              {formatPath(systemStore.workspacePath)}
            </span>
          </div>

          {systemStore.mcpCount > 0 && (
            <div className="flex items-center gap-2 border-l border-slate-800 pl-6 py-1" title="Loaded MCP Servers">
              <span className="text-[10px] font-mono text-slate-500 uppercase tracking-widest">MCP</span>
              <span className="text-[11px] font-mono text-emerald-400 truncate bg-emerald-900/30 px-2 py-0.5 rounded border border-emerald-800/50">
                {systemStore.mcpCount}
              </span>
            </div>
          )}

          {showFileSyncTip && (
            <div className="flex items-center gap-2 border-l border-slate-800 pl-6 py-1 animate-in fade-in slide-in-from-left-4">
              <span className="w-1.5 h-1.5 bg-emerald-500 rounded-full animate-ping"></span>
              <span className="text-[10px] font-bold text-emerald-500 uppercase tracking-widest">File tree synchronized</span>
            </div>
          )}
        </div>

        <button
          onClick={stopAgent}
          className={cn(
            'flex items-center gap-2 px-3 py-1 rounded border transition-all active:scale-95 group',
            isAgentBusy
              ? 'border-rose-500/80 bg-rose-500/20 hover:bg-rose-500/30 text-rose-400 shadow-[0_0_15px_rgba(244,63,94,0.3)] animate-pulse'
              : 'border-rose-500/30 bg-rose-500/5 hover:bg-rose-500/10 text-rose-500/70'
          )}
        >
          <div className="w-2 h-2 bg-rose-500 rounded-sm group-hover:scale-110 transition-transform"></div>
          <span className="text-[10px] font-bold uppercase tracking-wider">Stop Agent</span>
        </button>
      </div>
    </>
  )
}

export default StatusBar
