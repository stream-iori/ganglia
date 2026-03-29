import { useEffect, useState, useMemo, useRef, useCallback } from 'react';
import {
  ChevronDown,
  ChevronRight,
  Activity,
  Clock,
  Layers,
  FileJson,
  AlertCircle,
  CheckCircle2,
  RotateCcw,
  Radio,
} from 'lucide-react';

interface TraceEvent {
  sessionId: string;
  type: string;
  content: string;
  data: Record<string, unknown>;
  timestamp: number;
  spanId?: string;
  parentSpanId?: string;
}

interface SpanNode {
  spanId: string;
  startEvent: TraceEvent;
  endEvent?: TraceEvent;
  children: SpanNode[];
}

/** Merge start and end events to get the most useful display data */
function mergedEvent(node: SpanNode): TraceEvent {
  if (!node.endEvent) return node.startEvent;
  return {
    ...node.startEvent,
    data: { ...node.startEvent.data, ...node.endEvent.data },
  };
}

function getTypeColor(type: string) {
  if (type.includes('ERROR'))
    return 'bg-red-50 text-red-700 border-red-100 dark:bg-red-950/30 dark:text-red-400 dark:border-red-900/50';
  if (type.includes('SKILL'))
    return 'bg-purple-50 text-purple-700 border-purple-100 dark:bg-purple-950/30 dark:text-purple-400 dark:border-purple-900/50';
  if (type.includes('MCP'))
    return 'bg-orange-50 text-orange-700 border-orange-100 dark:bg-orange-950/30 dark:text-orange-400 dark:border-orange-900/50';
  if (type.includes('TOOL'))
    return 'bg-amber-50 text-amber-700 border-amber-100 dark:bg-amber-950/30 dark:text-amber-400 dark:border-amber-900/50';
  if (type.includes('MODEL') || type.includes('REASON'))
    return 'bg-blue-50 text-blue-700 border-blue-100 dark:bg-blue-950/30 dark:text-blue-400 dark:border-blue-900/50';
  if (type.includes('BUDGET'))
    return 'bg-blue-50 text-blue-700 border-blue-100 dark:bg-blue-950/30 dark:text-blue-400 dark:border-blue-900/50';
  if (type.includes('COMPRESS'))
    return 'bg-emerald-50 text-emerald-700 border-emerald-100 dark:bg-emerald-950/30 dark:text-emerald-400 dark:border-emerald-900/50';
  if (type.includes('SESSION'))
    return 'bg-indigo-50 text-indigo-700 border-indigo-100 dark:bg-indigo-950/30 dark:text-indigo-400 dark:border-indigo-900/50';
  if (type.includes('TURN'))
    return 'bg-cyan-50 text-cyan-700 border-cyan-100 dark:bg-cyan-950/30 dark:text-cyan-400 dark:border-cyan-900/50';
  return 'bg-slate-100 text-slate-700 border-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:border-slate-700';
}

function typeLabel(type: string) {
  return type
    .replace('_STARTED', '')
    .replace('_FINISHED', '')
    .replace('_RECORDED', '')
    .replace('_ALLOCATED', '');
}

function buildTreeForSession(events: TraceEvent[]): { tree: SpanNode[]; flat: TraceEvent[] } {
  const nodes: Record<string, SpanNode> = {};
  const rootNodes: SpanNode[] = [];
  const rootSpanIds = new Set<string>();
  const childSpanIds = new Set<string>();
  const flat: TraceEvent[] = [];

  // First pass: Create nodes from _STARTED events (or any first event per spanId)
  events.forEach((ev) => {
    if (!ev.spanId) {
      flat.push(ev);
      return;
    }
    if (!nodes[ev.spanId]) {
      nodes[ev.spanId] = { spanId: ev.spanId, startEvent: ev, children: [] };
    } else if (ev.type.endsWith('_STARTED')) {
      nodes[ev.spanId].startEvent = ev;
    }
  });

  // Second pass: Attach _FINISHED events and build relationships
  events.forEach((ev) => {
    if (!ev.spanId) return;
    const node = nodes[ev.spanId];
    if (ev.type.endsWith('_FINISHED')) {
      node.endEvent = ev;
    }

    if (ev.parentSpanId && nodes[ev.parentSpanId]) {
      if (!childSpanIds.has(node.spanId)) {
        childSpanIds.add(node.spanId);
        nodes[ev.parentSpanId].children.push(node);
      }
    } else if (!rootSpanIds.has(node.spanId)) {
      rootSpanIds.add(node.spanId);
      rootNodes.push(node);
    }
  });

  // Third pass: Sort children by timestamp
  Object.values(nodes).forEach((node) => {
    node.children.sort((a, b) => a.startEvent.timestamp - b.startEvent.timestamp);
  });
  rootNodes.sort((a, b) => a.startEvent.timestamp - b.startEvent.timestamp);

  return { tree: rootNodes, flat };
}

export default function TraceStudio() {
  const [files, setFiles] = useState<string[]>([]);
  const [selectedFile, setSelectedFile] = useState<string | null>(null);
  const [events, setEvents] = useState<TraceEvent[]>([]);
  const [expandedSpans, setExpandedSpans] = useState<Set<string>>(new Set());
  const [liveEvents, setLiveEvents] = useState<TraceEvent[]>([]);
  const [liveMode, setLiveMode] = useState(false);
  const [wsConnected, setWsConnected] = useState(false);
  const wsRef = useRef<WebSocket | null>(null);
  const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const reconnectAttemptsRef = useRef(0);
  const liveModeRef = useRef(false);
  const MAX_LIVE_EVENTS = 5000;

  // Fetch file list
  useEffect(() => {
    fetch('/api/traces')
      .then((res) => res.json())
      .then((data) => setFiles(data))
      .catch((err) => console.error(err));
  }, []);

  // Fetch selected file
  useEffect(() => {
    if (selectedFile) {
      setLiveMode(false);
      fetch(`/api/traces/${selectedFile}`)
        .then((res) => res.json())
        .then((data: TraceEvent[]) => {
          setEvents(data);
          const allSpans = data.filter((ev) => ev.spanId).map((ev) => ev.spanId!);
          setExpandedSpans(new Set(allSpans));
        })
        .catch((err) => console.error(err));
    }
  }, [selectedFile]);

  // WebSocket live connection with auto-reconnect
  const doConnect = useCallback(() => {
    if (wsRef.current) {
      wsRef.current.close();
    }

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const ws = new WebSocket(`${protocol}//${window.location.host}/ws/traces`);
    wsRef.current = ws;

    ws.onopen = () => {
      setWsConnected(true);
      reconnectAttemptsRef.current = 0;
    };

    ws.onmessage = (msg) => {
      try {
        const ev: TraceEvent = JSON.parse(msg.data);
        setLiveEvents((prev) => {
          const next = [...prev, ev];
          return next.length > MAX_LIVE_EVENTS ? next.slice(-MAX_LIVE_EVENTS) : next;
        });
        if (ev.spanId) {
          setExpandedSpans((prev) => new Set([...prev, ev.spanId!]));
        }
      } catch {
        // skip malformed
      }
    };

    ws.onclose = () => {
      setWsConnected(false);
      if (wsRef.current === ws) wsRef.current = null;
      // Auto-reconnect if still in live mode
      if (liveModeRef.current) {
        const delay = Math.min(1000 * Math.pow(2, reconnectAttemptsRef.current), 30000);
        reconnectAttemptsRef.current++;
        reconnectTimerRef.current = setTimeout(() => {
          if (liveModeRef.current) doConnect();
        }, delay);
      }
    };

    ws.onerror = () => {
      // onclose will fire after onerror
    };
  }, []);

  const connectLive = useCallback(() => {
    setSelectedFile(null);
    setLiveMode(true);
    liveModeRef.current = true;
    setLiveEvents([]);
    setExpandedSpans(new Set());
    reconnectAttemptsRef.current = 0;
    doConnect();
  }, [doConnect]);

  const disconnectLive = useCallback(() => {
    liveModeRef.current = false;
    setLiveMode(false);
    setWsConnected(false);
    if (reconnectTimerRef.current) {
      clearTimeout(reconnectTimerRef.current);
      reconnectTimerRef.current = null;
    }
    if (wsRef.current) {
      wsRef.current.close();
      wsRef.current = null;
    }
  }, []);

  useEffect(() => {
    return () => {
      liveModeRef.current = false;
      if (reconnectTimerRef.current) clearTimeout(reconnectTimerRef.current);
      wsRef.current?.close();
    };
  }, []);

  const displayEvents = liveMode ? liveEvents : events;

  // Group events by sessionId
  const sessionGroups = useMemo(() => {
    const groups = new Map<string, TraceEvent[]>();
    displayEvents.forEach((ev) => {
      const list = groups.get(ev.sessionId) || [];
      list.push(ev);
      groups.set(ev.sessionId, list);
    });
    return groups;
  }, [displayEvents]);

  // Build tree per session
  const sessionTrees = useMemo(() => {
    const trees = new Map<string, { tree: SpanNode[]; flat: TraceEvent[] }>();
    sessionGroups.forEach((evts, sid) => {
      trees.set(sid, buildTreeForSession(evts));
    });
    return trees;
  }, [sessionGroups]);

  const toggleSpan = (spanId: string, e: React.MouseEvent) => {
    e.stopPropagation();
    const newExpanded = new Set(expandedSpans);
    if (newExpanded.has(spanId)) {
      newExpanded.delete(spanId);
    } else {
      newExpanded.add(spanId);
    }
    setExpandedSpans(newExpanded);
  };

  const renderSpan = (node: SpanNode, depth = 0) => {
    const ev = mergedEvent(node);
    const spanId = node.spanId;
    const hasChildren = node.children.length > 0;
    const isExpanded = expandedSpans.has(spanId);

    const duration = ev.data?.durationMs as number | undefined;
    const attempt = ev.data?.attempt as number | undefined;
    const model = ev.data?.model as string | undefined;
    const status = ev.data?.status as string | undefined;
    // Use startEvent content for the label (tool name, user input etc.)
    const displayContent = node.startEvent.content || ev.content;

    return (
      <div key={spanId} className="relative">
        {depth > 0 && (
          <div
            className="absolute border-l border-slate-200 dark:border-slate-800"
            style={{
              left: `${(depth - 1) * 20 + 8}px`,
              top: '-8px',
              bottom: '12px',
            }}
          />
        )}

        <div
          onClick={(e) => hasChildren && toggleSpan(spanId, e)}
          className={`group mb-2 border p-3 rounded-xl transition-all cursor-pointer flex items-start gap-3
            ${
              isExpanded
                ? 'bg-white dark:bg-slate-900 border-emerald-200 dark:border-emerald-900/50 shadow-sm ring-1 ring-emerald-500/10'
                : 'bg-slate-50/50 dark:bg-slate-900/40 border-slate-200 dark:border-slate-800 hover:border-slate-300 dark:hover:border-slate-700 hover:bg-white dark:hover:bg-slate-900 shadow-none'
            }`}
          style={{ marginLeft: `${depth * 20}px` }}
        >
          {hasChildren ? (
            <div className="mt-1 text-slate-400 group-hover:text-emerald-500 transition-colors bg-white dark:bg-slate-800 rounded shadow-sm border border-slate-100 dark:border-slate-700">
              {isExpanded ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
            </div>
          ) : (
            <div className="mt-1.5 w-3.5 h-3.5 flex items-center justify-center">
              <div className="w-1.5 h-1.5 rounded-full bg-slate-300 dark:bg-slate-700" />
            </div>
          )}

          <div className="flex-1 min-w-0">
            <div className="flex justify-between items-start mb-1.5 gap-4">
              <div className="flex flex-wrap items-center gap-2">
                <span
                  className={`text-[10px] px-2 py-0.5 rounded-full font-bold uppercase tracking-tight shadow-sm border ${getTypeColor(node.startEvent.type)}`}
                >
                  {typeLabel(node.startEvent.type)}
                </span>

                {model && (
                  <span className="text-[10px] font-semibold text-slate-600 dark:text-slate-400 bg-slate-100 dark:bg-slate-800 px-2 py-0.5 rounded-full flex items-center gap-1.5 border border-slate-200 dark:border-slate-700">
                    <Activity size={10} className="text-blue-500" /> {model}
                  </span>
                )}

                {attempt && attempt > 1 && (
                  <span className="text-[10px] font-bold text-amber-600 dark:text-amber-400 bg-amber-50 dark:bg-amber-900/20 px-2 py-0.5 rounded-full flex items-center gap-1 border border-amber-100 dark:border-amber-900/50">
                    <RotateCcw size={10} /> Retry #{attempt}
                  </span>
                )}
              </div>

              <div className="flex items-center gap-3 shrink-0">
                {duration !== undefined && (
                  <span className="text-[10px] font-mono text-emerald-600 dark:text-emerald-400 font-bold bg-emerald-50 dark:bg-emerald-950/30 px-1.5 py-0.5 rounded border border-emerald-100 dark:border-emerald-900/50 flex items-center gap-1">
                    <Clock size={10} /> {duration}ms
                  </span>
                )}
                <span className="text-[10px] font-medium text-slate-400 dark:text-slate-500 font-mono">
                  {new Date(ev.timestamp).toLocaleTimeString([], {
                    hour12: false,
                    hour: '2-digit',
                    minute: '2-digit',
                    second: '2-digit',
                  })}
                </span>
              </div>
            </div>

            <div className="flex items-start gap-2">
              {status === 'failed' && (
                <AlertCircle size={14} className="text-red-500 mt-0.5 shrink-0" />
              )}
              {status === 'success' && (
                <CheckCircle2 size={14} className="text-emerald-500 mt-0.5 shrink-0" />
              )}
              <div className="text-sm text-slate-900 dark:text-slate-100 font-sans font-medium break-words">
                {node.startEvent.type === 'CONTEXT_BUDGET_ALLOCATED'
                  ? 'Context Budget Allocated'
                  : node.startEvent.type === 'CONTEXT_COMPRESSED'
                    ? 'Context Compression'
                    : displayContent ||
                      (node.startEvent.type === 'REASONING_STARTED' ? 'Thinking...' : '')}
              </div>
            </div>

            {ev.data && Object.keys(ev.data).length > 0 && isExpanded && (
              <div className="mt-3 text-[11px] text-slate-600 dark:text-slate-400 font-mono bg-white dark:bg-black/20 border border-slate-100 dark:border-slate-800/50 p-3 rounded-lg overflow-hidden shadow-inner">
                {node.startEvent.type === 'CONTEXT_BUDGET_ALLOCATED' ? (
                  (() => {
                    const d = ev.data;
                    const ctxLimit = d.contextLimit as number;
                    const maxGen = d.maxGenerationTokens as number;
                    const available = ctxLimit - maxGen;
                    const sys = d.systemPromptBudget as number;
                    const hist = d.historyBudget as number;
                    const tool = d.toolOutputBudget as number;
                    const obs = d.observationFallback as number;
                    const comp = d.compressionTarget as number;

                    const pct = (v: number) =>
                      available > 0 ? ((v / available) * 100).toFixed(1) : '0';
                    const segments = [
                      { label: 'System Prompt', value: sys, color: 'bg-blue-500' },
                      { label: 'History', value: hist, color: 'bg-emerald-500' },
                      { label: 'Tool Output', value: tool, color: 'bg-amber-500' },
                      { label: 'Obs. Fallback', value: obs, color: 'bg-purple-500' },
                    ];

                    return (
                      <div className="space-y-4">
                        {/* Summary table */}
                        <div className="grid grid-cols-2 gap-x-6 gap-y-1.5">
                          {[
                            ['Context Limit', ctxLimit],
                            ['Max Generation', maxGen],
                            ['Available', available],
                            ['System Prompt', sys],
                            ['History', hist],
                            ['Tool Output/msg', tool],
                            ['Obs. Fallback', obs],
                            ['Compression Target', comp],
                          ].map(([label, val]) => (
                            <div key={label as string} className="flex justify-between">
                              <span className="text-slate-400 uppercase text-[9px] font-bold">
                                {label as string}
                              </span>
                              <span className="text-blue-600 dark:text-blue-400 font-bold">
                                {(val as number).toLocaleString()}
                              </span>
                            </div>
                          ))}
                        </div>

                        {/* Horizontal stacked bar chart */}
                        <div>
                          <div className="text-[9px] text-slate-400 uppercase font-bold mb-1.5">
                            Budget Allocation
                          </div>
                          <div className="relative h-7 flex rounded-lg overflow-hidden border border-slate-200 dark:border-slate-700">
                            {segments.map((seg) => (
                              <div
                                key={seg.label}
                                className={`${seg.color} opacity-80 hover:opacity-100 transition-opacity relative group`}
                                style={{
                                  width: `${pct(seg.value)}%`,
                                  minWidth: seg.value > 0 ? '2px' : '0',
                                }}
                                title={`${seg.label}: ${seg.value.toLocaleString()} (${pct(seg.value)}%)`}
                              >
                                {parseFloat(pct(seg.value)) > 8 && (
                                  <span className="absolute inset-0 flex items-center justify-center text-white text-[8px] font-bold truncate px-1">
                                    {seg.label}
                                  </span>
                                )}
                              </div>
                            ))}
                          </div>
                          {/* Compression target marker */}
                          {available > 0 && (
                            <div className="relative h-1 mt-0.5">
                              <div
                                className="absolute top-0 h-3 border-l-2 border-dashed border-slate-400 dark:border-slate-500"
                                style={{ left: `${pct(comp)}%` }}
                                title={`Compression target: ${comp.toLocaleString()} (${pct(comp)}%)`}
                              />
                              <span
                                className="absolute text-[8px] text-slate-400 font-bold top-3"
                                style={{ left: `${pct(comp)}%`, transform: 'translateX(-50%)' }}
                              >
                                compress: {pct(comp)}%
                              </span>
                            </div>
                          )}
                          {/* Legend */}
                          <div className="flex flex-wrap gap-3 mt-5 text-[9px]">
                            {segments.map((seg) => (
                              <div key={seg.label} className="flex items-center gap-1.5">
                                <div className={`w-2.5 h-2.5 rounded-sm ${seg.color} opacity-80`} />
                                <span className="text-slate-500">
                                  {seg.label}:{' '}
                                  <b className="text-slate-700 dark:text-slate-300">
                                    {seg.value.toLocaleString()}
                                  </b>{' '}
                                  ({pct(seg.value)}%)
                                </span>
                              </div>
                            ))}
                          </div>
                        </div>
                      </div>
                    );
                  })()
                ) : node.startEvent.type === 'CONTEXT_COMPRESSED' ? (
                  <div className="flex flex-wrap gap-4">
                    {ev.data.beforeTokens !== undefined && (
                      <span className="flex items-center gap-1.5">
                        <span className="text-slate-400 uppercase text-[9px] font-bold">
                          Before:
                        </span>
                        <b className="text-slate-900 dark:text-slate-200">
                          {ev.data.beforeTokens as number}
                        </b>
                      </span>
                    )}
                    {ev.data.afterTokens !== undefined && (
                      <span className="flex items-center gap-1.5">
                        <span className="text-slate-400 uppercase text-[9px] font-bold">
                          After:
                        </span>
                        <b className="text-emerald-600 dark:text-emerald-400">
                          {ev.data.afterTokens as number}
                        </b>
                      </span>
                    )}
                    {ev.data.contextLimit !== undefined && (
                      <span className="flex items-center gap-1.5 border-l border-slate-200 dark:border-slate-800 pl-4">
                        <span className="text-slate-400 uppercase text-[9px] font-bold">
                          Limit:
                        </span>
                        <span className="text-slate-600 dark:text-slate-400">
                          {ev.data.contextLimit as number}
                        </span>
                      </span>
                    )}
                  </div>
                ) : node.startEvent.type === 'TOKEN_USAGE_RECORDED' ? (
                  <div className="flex flex-wrap gap-6 text-blue-600 dark:text-blue-400 font-bold">
                    <span className="flex flex-col">
                      <span className="text-slate-400 uppercase text-[8px] mb-0.5">Prompt</span>
                      {ev.data.promptTokens as number}
                    </span>
                    <span className="flex flex-col">
                      <span className="text-slate-400 uppercase text-[8px] mb-0.5">Completion</span>
                      {ev.data.completionTokens as number}
                    </span>
                    <span className="flex flex-col border-l border-slate-200 dark:border-slate-800 pl-6">
                      <span className="text-slate-400 uppercase text-[8px] mb-0.5">Total</span>
                      {ev.data.totalTokens as number}
                    </span>
                  </div>
                ) : (
                  <div className="max-h-64 overflow-y-auto custom-scrollbar whitespace-pre-wrap space-y-1.5">
                    {Object.entries(ev.data).map(([k, v]) => {
                      if (['durationMs', 'attempt', 'model', 'status', 'toolCallId'].includes(k))
                        return null;
                      return (
                        <div key={k} className="flex gap-3 group/item">
                          <span className="text-slate-400 dark:text-slate-500 shrink-0 font-bold uppercase text-[9px] mt-0.5 w-24">
                            {k}:
                          </span>
                          <span className="text-slate-700 dark:text-slate-300 break-all leading-relaxed">
                            {typeof v === 'object' ? JSON.stringify(v, null, 2) : String(v)}
                          </span>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
        {isExpanded && node.children.map((child) => renderSpan(child, depth + 1))}
      </div>
    );
  };

  const renderFlatEvent = (ev: TraceEvent, i: number) => (
    <div
      key={`${ev.timestamp}-${ev.type}-${i}`}
      className="border border-slate-200 dark:border-slate-800 p-5 rounded-2xl bg-slate-50/50 dark:bg-slate-900/50 flex flex-col gap-3 shadow-sm hover:shadow-md transition-all"
    >
      <div className="flex justify-between items-center">
        <span className="text-[10px] font-black text-slate-400 dark:text-slate-500 uppercase tracking-[0.2em] bg-white dark:bg-slate-800 px-2 py-1 rounded-md border border-slate-100 dark:border-slate-700">
          {ev.type}
        </span>
        <span className="text-[10px] font-bold text-slate-400 font-mono bg-slate-100 dark:bg-slate-800 px-2 py-1 rounded">
          {new Date(ev.timestamp).toLocaleTimeString()}
        </span>
      </div>
      <div className="text-sm text-slate-800 dark:text-slate-200 leading-relaxed font-medium">
        {ev.content}
      </div>
      {ev.data && Object.keys(ev.data).length > 0 && (
        <div className="text-[10px] font-mono bg-white dark:bg-black/40 p-3 rounded-xl border border-slate-100 dark:border-slate-800/50 shadow-inner overflow-x-auto">
          {JSON.stringify(ev.data, null, 2)}
        </div>
      )}
    </div>
  );

  const renderSessionTree = (sessionId: string) => {
    const result = sessionTrees.get(sessionId) || { tree: [], flat: [] };
    const sessionEvents = sessionGroups.get(sessionId) || [];

    return (
      <div key={sessionId} className="mb-10">
        <div className="flex items-center gap-3 mb-4 pb-3 border-b border-slate-100 dark:border-slate-800">
          <span className="px-2.5 py-1 bg-indigo-50 dark:bg-indigo-500/10 text-indigo-600 dark:text-indigo-400 text-[10px] font-bold rounded-lg uppercase tracking-wider border border-indigo-100 dark:border-indigo-500/20">
            Session
          </span>
          <span className="text-sm font-mono text-slate-500 dark:text-slate-400 truncate">
            {sessionId}
          </span>
          <span className="text-[10px] text-slate-400 dark:text-slate-600 ml-auto">
            {sessionEvents.length} events
          </span>
        </div>

        {result.tree.length > 0 && (
          <div className="space-y-1">{result.tree.map((node) => renderSpan(node))}</div>
        )}

        {result.flat.length > 0 && (
          <div className="space-y-4 mt-4">{result.flat.map((ev, i) => renderFlatEvent(ev, i))}</div>
        )}

        {result.tree.length === 0 && result.flat.length === 0 && (
          <div className="text-sm text-slate-400 dark:text-slate-500 py-4">No events</div>
        )}
      </div>
    );
  };

  const sessionIds = [...sessionGroups.keys()];

  return (
    <div className="flex w-full h-full bg-white dark:bg-slate-950 text-slate-900 dark:text-slate-100 transition-colors duration-200 font-sans antialiased">
      <div className="w-80 border-r border-slate-200 dark:border-slate-800 flex flex-col shrink-0 bg-slate-50/80 dark:bg-slate-950/50 backdrop-blur-xl">
        <div className="p-5 border-b border-slate-200 dark:border-slate-800 font-bold flex items-center justify-between text-slate-900 dark:text-white">
          <div className="flex items-center gap-2.5">
            <div className="p-1.5 bg-emerald-500 rounded-lg shadow-lg shadow-emerald-500/20">
              <Layers size={18} className="text-white" />
            </div>
            <span className="tracking-tight text-lg">Trace Studio</span>
          </div>
          <div className="text-[10px] bg-slate-200 dark:bg-slate-800 px-2 py-0.5 rounded text-slate-500 dark:text-slate-400 font-mono">
            v0.2.0
          </div>
        </div>

        {/* Live mode button */}
        <div className="p-3 border-b border-slate-200 dark:border-slate-800">
          <button
            onClick={liveMode ? disconnectLive : connectLive}
            className={`w-full flex items-center justify-center gap-2 px-3 py-2.5 rounded-xl text-xs font-bold uppercase tracking-wider transition-all border ${
              liveMode
                ? 'bg-red-50 dark:bg-red-500/10 text-red-600 dark:text-red-400 border-red-200 dark:border-red-500/30 shadow-sm'
                : 'bg-emerald-50 dark:bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border-emerald-200 dark:border-emerald-500/30 hover:shadow-sm'
            }`}
          >
            <Radio size={14} className={liveMode ? 'animate-pulse' : ''} />
            {liveMode ? (wsConnected ? 'Live' : 'Reconnecting...') : 'Connect Live'}
          </button>
        </div>

        <div className="overflow-y-auto flex-1 p-3 custom-scrollbar space-y-1">
          {files.length === 0 && !liveMode && (
            <div className="text-center text-slate-400 dark:text-slate-600 mt-16 text-xs flex flex-col items-center gap-3">
              <div className="p-4 bg-slate-100 dark:bg-slate-900 rounded-full">
                <FileJson size={28} className="opacity-20" />
              </div>
              No traces found in .ganglia/trace
            </div>
          )}
          {files.map((f) => (
            <div
              key={f}
              onClick={() => setSelectedFile(f)}
              className={`group cursor-pointer p-3.5 rounded-xl transition-all border ${
                selectedFile === f && !liveMode
                  ? 'bg-white dark:bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border-emerald-200 dark:border-emerald-500/30 shadow-md shadow-emerald-500/5'
                  : 'hover:bg-white dark:hover:bg-slate-900 text-slate-600 dark:text-slate-400 border-transparent hover:border-slate-200 dark:hover:border-slate-800'
              }`}
            >
              <div className="flex items-center gap-3 mb-1.5">
                <div
                  className={`p-1.5 rounded-lg transition-colors ${selectedFile === f && !liveMode ? 'bg-emerald-50 dark:bg-emerald-500/20 text-emerald-600' : 'bg-slate-100 dark:bg-slate-800 text-slate-400 group-hover:bg-emerald-50 dark:group-hover:bg-emerald-500/10 group-hover:text-emerald-500'}`}
                >
                  <FileJson size={14} />
                </div>
                <span className="font-semibold truncate text-[13px] tracking-tight">
                  {f.replace('.jsonl', '')}
                </span>
              </div>
              <div className="flex justify-between items-center pl-9">
                <span className="text-[10px] font-medium opacity-50 uppercase tracking-widest">
                  Trace File
                </span>
                <span className="text-[10px] group-hover:translate-x-0.5 transition-transform opacity-0 group-hover:opacity-100 font-bold">
                  View →
                </span>
              </div>
            </div>
          ))}
        </div>
      </div>
      <div className="flex-1 overflow-y-auto p-10 bg-white dark:bg-slate-950 custom-scrollbar scroll-smooth">
        {selectedFile || liveMode ? (
          <div className="max-w-5xl mx-auto">
            <header className="flex justify-between items-end mb-10 border-b border-slate-100 dark:border-slate-800 pb-8">
              <div>
                <div className="flex items-center gap-2 mb-2">
                  <span
                    className={`px-2 py-0.5 text-[10px] font-bold rounded uppercase tracking-wider border ${
                      liveMode
                        ? 'bg-red-500/10 text-red-600 dark:text-red-400 border-red-500/20'
                        : 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border-emerald-500/20'
                    }`}
                  >
                    {liveMode ? 'Live Stream' : 'Trace File'}
                  </span>
                </div>
                <h2 className="text-3xl font-black text-slate-900 dark:text-white tracking-tighter">
                  {liveMode ? 'Live Trace' : selectedFile?.replace('.jsonl', '')}
                </h2>
                <p className="text-sm text-slate-500 dark:text-slate-400 mt-2 font-medium">
                  {sessionIds.length > 1
                    ? `${sessionIds.length} sessions`
                    : 'Hierarchical execution flow and performance analysis'}
                </p>
              </div>
              <div className="flex flex-col items-end gap-2">
                <div className="text-[11px] font-bold text-slate-400 dark:text-slate-500 bg-slate-50 dark:bg-slate-900 px-4 py-2 rounded-full border border-slate-200 dark:border-slate-800 shadow-sm flex items-center gap-2">
                  <Activity size={12} className="text-emerald-500" />
                  {displayEvents.length} EVENTS
                </div>
              </div>
            </header>

            {sessionIds.length > 0 ? (
              <div className="animate-in fade-in slide-in-from-bottom-2 duration-500">
                {sessionIds.map((sid) => renderSessionTree(sid))}
              </div>
            ) : (
              <div className="text-center text-slate-400 dark:text-slate-500 py-20">
                {liveMode ? 'Waiting for events...' : 'No events in this trace file'}
              </div>
            )}
          </div>
        ) : (
          <div className="flex h-full flex-col items-center justify-center text-slate-400 dark:text-slate-500 gap-8">
            <div className="relative">
              <div className="absolute inset-0 bg-emerald-500/20 blur-3xl rounded-full" />
              <div className="relative w-24 h-24 rounded-3xl bg-white dark:bg-slate-900 flex items-center justify-center border border-slate-100 dark:border-slate-800 shadow-2xl">
                <Layers size={48} className="text-emerald-500" />
              </div>
            </div>
            <div className="text-center space-y-2">
              <h3 className="text-xl font-bold text-slate-900 dark:text-white tracking-tight">
                Trace Studio Ready
              </h3>
              <p className="text-sm max-w-sm mx-auto text-slate-500 dark:text-slate-400 leading-relaxed">
                Select an execution trace from the sidebar or connect live to visualize agent
                reasoning, tool usage, and performance metrics.
              </p>
            </div>
            <div className="flex gap-6 mt-4">
              {[
                {
                  icon: Activity,
                  label: 'Performance',
                  color: 'text-emerald-500',
                  bg: 'bg-emerald-50 dark:bg-emerald-500/10',
                },
                {
                  icon: Layers,
                  label: 'Hierarchy',
                  color: 'text-blue-500',
                  bg: 'bg-blue-50 dark:bg-blue-500/10',
                },
                {
                  icon: Radio,
                  label: 'Live',
                  color: 'text-red-500',
                  bg: 'bg-red-50 dark:bg-red-500/10',
                },
              ].map((item, i) => (
                <div key={i} className="flex flex-col items-center gap-2 group cursor-default">
                  <div
                    className={`p-3 rounded-2xl ${item.bg} ${item.color} border border-transparent group-hover:border-current transition-all duration-300`}
                  >
                    <item.icon size={20} />
                  </div>
                  <span className="text-[10px] font-bold uppercase tracking-wider opacity-60">
                    {item.label}
                  </span>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
