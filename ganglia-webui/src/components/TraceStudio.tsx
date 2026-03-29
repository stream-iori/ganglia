import { useEffect, useState, useMemo } from 'react';
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
  event: TraceEvent;
  children: SpanNode[];
}

export default function TraceStudio() {
  const [files, setFiles] = useState<string[]>([]);
  const [selectedFile, setSelectedFile] = useState<string | null>(null);
  const [events, setEvents] = useState<TraceEvent[]>([]);
  const [expandedSpans, setExpandedSpans] = useState<Set<string>>(new Set());

  useEffect(() => {
    fetch('/api/traces')
      .then((res) => res.json())
      .then((data) => setFiles(data))
      .catch((err) => console.error(err));
  }, []);

  useEffect(() => {
    if (selectedFile) {
      fetch(`/api/traces/${selectedFile}`)
        .then((res) => res.json())
        .then((data) => {
          setEvents(data);
          // Auto-expand all spans on load for better visibility, or just the first level
          const allSpans = data
            .filter((ev: TraceEvent) => ev.spanId)
            .map((ev: TraceEvent) => ev.spanId!);
          setExpandedSpans(new Set(allSpans));
        })
        .catch((err) => console.error(err));
    }
  }, [selectedFile]);

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

  // Build the tree from flat events
  const traceTree = useMemo(() => {
    const nodes: Record<string, SpanNode> = {};
    const rootNodes: SpanNode[] = [];

    // First pass: Create nodes for all events with spanId
    events.forEach((ev) => {
      if (ev.spanId && !nodes[ev.spanId]) {
        nodes[ev.spanId] = { event: ev, children: [] };
      }
    });

    // Second pass: Build relationships
    events.forEach((ev) => {
      if (ev.spanId) {
        const node = nodes[ev.spanId];
        // Ensure node has the most complete event data (e.g. FINISHED event often has more data)
        if (ev.type.endsWith('_FINISHED') || !node.event.type.endsWith('_FINISHED')) {
          node.event = ev;
        }

        if (ev.parentSpanId && nodes[ev.parentSpanId]) {
          if (!nodes[ev.parentSpanId].children.includes(node)) {
            nodes[ev.parentSpanId].children.push(node);
          }
        } else if (!ev.parentSpanId) {
          if (!rootNodes.includes(node)) rootNodes.push(node);
        } else {
          // parentSpanId exists but parent node not found yet
          // In a robust tree, we might want to still show this as a root or handle it
          if (!rootNodes.includes(node)) rootNodes.push(node);
        }
      }
    });

    // Third pass: Sort children by timestamp
    Object.values(nodes).forEach((node) => {
      node.children.sort((a, b) => a.event.timestamp - b.event.timestamp);
    });
    rootNodes.sort((a, b) => a.event.timestamp - b.event.timestamp);

    return rootNodes;
  }, [events]);

  const renderSpan = (node: SpanNode, depth = 0) => {
    const ev = node.event;
    const spanId = ev.spanId || `random-${Math.random()}`;
    const hasChildren = node.children.length > 0;
    const isExpanded = expandedSpans.has(spanId);

    const duration = ev.data?.durationMs as number | undefined;
    const attempt = ev.data?.attempt as number | undefined;
    const model = ev.data?.model as string | undefined;
    const status = ev.data?.status as string | undefined;

    return (
      <div key={spanId} className="relative">
        {/* Tree vertical guide line */}
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
                  className={`text-[10px] px-2 py-0.5 rounded-full font-bold uppercase tracking-tight shadow-sm border ${
                    ev.type.includes('ERROR')
                      ? 'bg-red-50 text-red-700 border-red-100 dark:bg-red-950/30 dark:text-red-400 dark:border-red-900/50'
                      : ev.type.includes('SKILL')
                        ? 'bg-purple-50 text-purple-700 border-purple-100 dark:bg-purple-950/30 dark:text-purple-400 dark:border-purple-900/50'
                        : ev.type.includes('MCP')
                          ? 'bg-orange-50 text-orange-700 border-orange-100 dark:bg-orange-950/30 dark:text-orange-400 dark:border-orange-900/50'
                          : ev.type.includes('TOOL')
                            ? 'bg-amber-50 text-amber-700 border-amber-100 dark:bg-amber-950/30 dark:text-amber-400 dark:border-amber-900/50'
                            : ev.type.includes('MODEL') || ev.type.includes('REASON')
                              ? 'bg-blue-50 text-blue-700 border-blue-100 dark:bg-blue-950/30 dark:text-blue-400 dark:border-blue-900/50'
                              : ev.type.includes('COMPRESS')
                                ? 'bg-emerald-50 text-emerald-700 border-emerald-100 dark:bg-emerald-950/30 dark:text-emerald-400 dark:border-emerald-900/50'
                                : 'bg-slate-100 text-slate-700 border-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:border-slate-700'
                  }`}
                >
                  {ev.type
                    .replace('_STARTED', '')
                    .replace('_FINISHED', '')
                    .replace('_RECORDED', '')}
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
                {ev.type === 'CONTEXT_COMPRESSED'
                  ? '🔄 Context Compression Triggered'
                  : ev.content === 'context_compression_finished'
                    ? '✅ Context Compression Finished'
                    : ev.content || (ev.type === 'REASONING_STARTED' ? 'Thinking...' : '')}
              </div>
            </div>

            {ev.data && Object.keys(ev.data).length > 0 && isExpanded && (
              <div className="mt-3 text-[11px] text-slate-600 dark:text-slate-400 font-mono bg-white dark:bg-black/20 border border-slate-100 dark:border-slate-800/50 p-3 rounded-lg overflow-hidden shadow-inner">
                {ev.type === 'CONTEXT_COMPRESSED' ||
                ev.content === 'context_compression_finished' ? (
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
                ) : ev.type === 'TOKEN_USAGE_RECORDED' ? (
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
            v0.1.7
          </div>
        </div>
        <div className="overflow-y-auto flex-1 p-3 custom-scrollbar space-y-1">
          {files.length === 0 && (
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
                selectedFile === f
                  ? 'bg-white dark:bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border-emerald-200 dark:border-emerald-500/30 shadow-md shadow-emerald-500/5'
                  : 'hover:bg-white dark:hover:bg-slate-900 text-slate-600 dark:text-slate-400 border-transparent hover:border-slate-200 dark:hover:border-slate-800'
              }`}
            >
              <div className="flex items-center gap-3 mb-1.5">
                <div
                  className={`p-1.5 rounded-lg transition-colors ${selectedFile === f ? 'bg-emerald-50 dark:bg-emerald-500/20 text-emerald-600' : 'bg-slate-100 dark:bg-slate-800 text-slate-400 group-hover:bg-emerald-50 dark:group-hover:bg-emerald-500/10 group-hover:text-emerald-500'}`}
                >
                  <FileJson size={14} />
                </div>
                <span className="font-semibold truncate text-[13px] tracking-tight">
                  {f.replace('.jsonl', '')}
                </span>
              </div>
              <div className="flex justify-between items-center pl-9">
                <span className="text-[10px] font-medium opacity-50 uppercase tracking-widest">
                  Session
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
        {selectedFile ? (
          <div className="max-w-5xl mx-auto">
            <header className="flex justify-between items-end mb-10 border-b border-slate-100 dark:border-slate-800 pb-8">
              <div>
                <div className="flex items-center gap-2 mb-2">
                  <span className="px-2 py-0.5 bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 text-[10px] font-bold rounded uppercase tracking-wider border border-emerald-500/20">
                    Live Session
                  </span>
                </div>
                <h2 className="text-3xl font-black text-slate-900 dark:text-white tracking-tighter">
                  {selectedFile.replace('.jsonl', '')}
                </h2>
                <p className="text-sm text-slate-500 dark:text-slate-400 mt-2 font-medium">
                  Hierarchical execution flow and performance analysis
                </p>
              </div>
              <div className="flex flex-col items-end gap-2">
                <div className="text-[11px] font-bold text-slate-400 dark:text-slate-500 bg-slate-50 dark:bg-slate-900 px-4 py-2 rounded-full border border-slate-200 dark:border-slate-800 shadow-sm flex items-center gap-2">
                  <Activity size={12} className="text-emerald-500" />
                  {events.length} EVENTS RECORDED
                </div>
              </div>
            </header>

            {traceTree.length > 0 ? (
              <div className="space-y-1 animate-in fade-in slide-in-from-bottom-2 duration-500">
                {traceTree.map((node) => renderSpan(node))}
              </div>
            ) : (
              <div className="space-y-4">
                {events.map((ev, i) => (
                  <div
                    key={i}
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
                ))}
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
                Select an execution trace from the sidebar to visualize agent reasoning, tool usage,
                and performance metrics in real-time.
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
                  icon: FileJson,
                  label: 'Deep Inspect',
                  color: 'text-amber-500',
                  bg: 'bg-amber-50 dark:bg-amber-500/10',
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
