import { useEffect, useState, useRef, useCallback } from 'react';
import { Activity, Pause, Play, Trash2 } from 'lucide-react';
import { cn } from '@/lib/utils';

interface TraceEvent {
  id: string;
  type: string;
  content: string;
  timestamp: number;
  data?: Record<string, unknown>;
}

export function TracePage() {
  const [events, setEvents] = useState<TraceEvent[]>([]);
  const [paused, setPaused] = useState(false);
  const [connected, setConnected] = useState(false);
  const wsRef = useRef<WebSocket | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);

  const connectWs = useCallback(() => {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const ws = new WebSocket(`${protocol}//${window.location.host}/ws/traces`);

    ws.onopen = () => setConnected(true);
    ws.onclose = () => {
      setConnected(false);
      setTimeout(connectWs, 3000);
    };
    ws.onmessage = (event) => {
      if (paused) return;
      try {
        const data = JSON.parse(event.data);
        const traceEvent: TraceEvent = {
          id: `${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
          type: data.type || 'UNKNOWN',
          content: data.content || data.message || JSON.stringify(data),
          timestamp: Date.now(),
          data,
        };
        setEvents((prev) => [...prev.slice(-500), traceEvent]);
      } catch {
        // ignore
      }
    };

    wsRef.current = ws;
  }, [paused]);

  useEffect(() => {
    connectWs();
    return () => {
      wsRef.current?.close();
    };
  }, [connectWs]);

  useEffect(() => {
    if (!paused && scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [events, paused]);

  const typeColors: Record<string, string> = {
    MANAGER_CYCLE_STARTED: 'text-violet-400',
    MANAGER_CYCLE_FINISHED: 'text-violet-500',
    MANAGER_GRAPH_CONVERGED: 'text-emerald-400',
    MANAGER_GRAPH_STALLED: 'text-amber-400',
    FACT_PUBLISHED: 'text-teal-400',
    FACT_SUPERSEDED: 'text-teal-600',
    TOKEN_RECEIVED: 'text-muted-foreground',
    TOOL_STARTED: 'text-blue-400',
    TOOL_FINISHED: 'text-blue-500',
    ERROR: 'text-red-500',
  };

  return (
    <div className="p-6 max-w-6xl mx-auto space-y-4 h-[calc(100vh-3.5rem)] flex flex-col">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <h1 className="text-2xl font-bold">Trace</h1>
          <div className="flex items-center gap-1.5">
            <div
              className={cn(
                'w-2 h-2 rounded-full',
                connected ? 'bg-emerald-500' : 'bg-red-500'
              )}
            />
            <span className="text-xs text-muted-foreground">
              {connected ? 'Connected' : 'Disconnected'}
            </span>
          </div>
        </div>
        <div className="flex gap-2">
          <button
            onClick={() => setPaused(!paused)}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-md border border-input text-sm hover:bg-accent"
          >
            {paused ? <Play className="h-3.5 w-3.5" /> : <Pause className="h-3.5 w-3.5" />}
            {paused ? 'Resume' : 'Pause'}
          </button>
          <button
            onClick={() => setEvents([])}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-md border border-input text-sm hover:bg-accent"
          >
            <Trash2 className="h-3.5 w-3.5" />
            Clear
          </button>
        </div>
      </div>

      <div
        ref={scrollRef}
        className="flex-1 rounded-lg border border-border bg-card p-4 overflow-y-auto font-mono text-xs space-y-1"
      >
        {events.length === 0 ? (
          <div className="flex items-center justify-center h-full text-muted-foreground">
            <Activity className="h-5 w-5 mr-2" />
            Waiting for trace events...
          </div>
        ) : (
          events.map((event) => (
            <div key={event.id} className="flex gap-2">
              <span className="text-muted-foreground shrink-0">
                {new Date(event.timestamp).toLocaleTimeString()}
              </span>
              <span
                className={cn(
                  'shrink-0 w-40 truncate font-medium',
                  typeColors[event.type] || 'text-foreground'
                )}
              >
                {event.type}
              </span>
              <span className="text-muted-foreground truncate">{event.content}</span>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
