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
      <div key={spanId} className="mb-2">
        <div
          onClick={(e) => hasChildren && toggleSpan(spanId, e)}
          className={`border border-slate-200 dark:border-slate-700 p-3 rounded-md bg-slate-50 dark:bg-slate-900 hover:bg-slate-100 dark:hover:bg-slate-800 transition-all cursor-pointer group flex items-start gap-3 ${isExpanded ? 'ring-1 ring-emerald-500/20 shadow-sm' : ''}`}
          style={{ marginLeft: `${depth * 20}px` }}
        >
          {hasChildren ? (
            <div className="mt-1 text-slate-400 group-hover:text-emerald-500 transition-colors">
              {isExpanded ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
            </div>
          ) : (
            <div className="mt-1 w-4 h-4" />
          )}

          <div className="flex-1">
            <div className="flex justify-between items-start mb-1">
              <div className="flex flex-wrap items-center gap-2">
                <span
                  className={`text-[9px] px-1.5 py-0.5 rounded font-bold uppercase tracking-wider ${
                    ev.type.includes('ERROR')
                      ? 'bg-red-100 text-red-700 dark:bg-red-900/50 dark:text-red-200'
                      : ev.type.includes('SKILL')
                        ? 'bg-purple-100 text-purple-700 dark:bg-purple-900/50 dark:text-purple-200'
                        : ev.type.includes('MCP')
                          ? 'bg-orange-100 text-orange-700 dark:bg-orange-900/50 dark:text-orange-200'
                          : ev.type.includes('TOOL')
                            ? 'bg-amber-100 text-amber-700 dark:bg-amber-900/50 dark:text-amber-200'
                            : ev.type.includes('MODEL') || ev.type.includes('REASON')
                              ? 'bg-blue-100 text-blue-700 dark:bg-blue-900/50 dark:text-blue-200'
                              : ev.type.includes('COMPRESS')
                                ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/50 dark:text-emerald-200'
                                : 'bg-slate-200 text-slate-700 dark:bg-slate-700 dark:text-slate-200'
                  }`}
                >
                  {ev.type
                    .replace('_STARTED', '')
                    .replace('_FINISHED', '')
                    .replace('_RECORDED', '')}
                </span>
                <span className="font-mono text-[10px] text-slate-400 dark:text-slate-500 opacity-0 group-hover:opacity-100 transition-opacity">
                  {ev.spanId}
                </span>

                {model && (
                  <span className="text-[10px] font-medium text-slate-500 dark:text-slate-400 bg-slate-200/50 dark:bg-slate-800 px-1 rounded flex items-center gap-1">
                    <Activity size={10} /> {model}
                  </span>
                )}

                {attempt && attempt > 1 && (
                  <span className="text-[10px] font-bold text-amber-600 dark:text-amber-400 flex items-center gap-1">
                    <RotateCcw size={10} /> Retry #{attempt}
                  </span>
                )}
              </div>

              <div className="flex items-center gap-3">
                {duration !== undefined && (
                  <span className="text-[10px] font-mono text-emerald-600 dark:text-emerald-500 font-bold flex items-center gap-1">
                    <Clock size={10} /> {duration}ms
                  </span>
                )}
                <span className="text-[10px] text-slate-400 dark:text-slate-500">
                  {new Date(ev.timestamp).toLocaleTimeString()}
                </span>
              </div>
            </div>

            <div className="flex items-center gap-2">
              {status === 'failed' && <AlertCircle size={14} className="text-red-500" />}
              {status === 'success' && <CheckCircle2 size={14} className="text-emerald-500" />}
              <div className="text-sm text-slate-700 dark:text-slate-300 font-sans font-medium">
                {ev.type === 'CONTEXT_COMPRESSED'
                  ? '🔄 Context Compression Triggered'
                  : ev.content === 'context_compression_finished'
                    ? '✅ Context Compression Finished'
                    : ev.content || (ev.type === 'REASONING_STARTED' ? 'Thinking...' : '')}
              </div>
            </div>

            {ev.data && Object.keys(ev.data).length > 0 && isExpanded && (
              <div className="mt-2 text-[10px] text-slate-500 font-mono bg-white/50 dark:bg-black/30 border border-slate-100 dark:border-transparent p-2 rounded overflow-hidden">
                {ev.type === 'CONTEXT_COMPRESSED' ||
                ev.content === 'context_compression_finished' ? (
                  <div className="flex gap-4">
                    {ev.data.beforeTokens !== undefined && (
                      <span>
                        Before:{' '}
                        <b className="text-slate-700 dark:text-slate-300">
                          {ev.data.beforeTokens as number}
                        </b>
                      </span>
                    )}
                    {ev.data.afterTokens !== undefined && (
                      <span>
                        After:{' '}
                        <b className="text-emerald-600 dark:text-emerald-400">
                          {ev.data.afterTokens as number}
                        </b>
                      </span>
                    )}
                    {ev.data.contextLimit !== undefined && (
                      <span>Limit: {ev.data.contextLimit as number}</span>
                    )}
                  </div>
                ) : ev.type === 'TOKEN_USAGE_RECORDED' ? (
                  <div className="flex gap-4 text-blue-600 dark:text-blue-400 font-bold">
                    <span>Prompt: {ev.data.promptTokens as number}</span>
                    <span>Completion: {ev.data.completionTokens as number}</span>
                    <span>Total: {ev.data.totalTokens as number}</span>
                  </div>
                ) : (
                  <div className="max-h-48 overflow-y-auto custom-scrollbar whitespace-pre-wrap">
                    {Object.entries(ev.data).map(([k, v]) => {
                      if (['durationMs', 'attempt', 'model', 'status'].includes(k)) return null;
                      return (
                        <div key={k} className="flex gap-2 mb-1">
                          <span className="text-slate-400 dark:text-slate-600 shrink-0">{k}:</span>
                          <span className="text-slate-600 dark:text-slate-400 break-all">
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
    <div className="flex w-full h-full bg-white dark:bg-slate-950 text-slate-900 dark:text-slate-200">
      <div className="w-72 border-r border-slate-200 dark:border-slate-800 flex flex-col shrink-0 bg-slate-50/50 dark:bg-transparent">
        <div className="p-4 border-b border-slate-200 dark:border-slate-800 font-bold flex items-center gap-2 text-slate-700 dark:text-slate-200">
          <Layers size={18} className="text-emerald-500" />
          Trace Studio
        </div>
        <div className="overflow-y-auto flex-1 p-2 custom-scrollbar">
          {files.length === 0 && (
            <div className="text-center text-slate-400 dark:text-slate-600 mt-10 text-xs flex flex-col items-center gap-2">
              <FileJson size={24} className="opacity-20" />
              No traces found
            </div>
          )}
          {files.map((f) => (
            <div
              key={f}
              onClick={() => setSelectedFile(f)}
              className={`group cursor-pointer p-3 text-xs rounded mb-2 transition-all border ${selectedFile === f ? 'bg-emerald-50 dark:bg-emerald-900/20 text-emerald-700 dark:text-emerald-100 border-emerald-200 dark:border-emerald-800 shadow-sm' : 'hover:bg-slate-200/50 dark:hover:bg-slate-800/50 text-slate-500 dark:text-slate-400 border-transparent hover:border-slate-200 dark:hover:border-slate-700'}`}
            >
              <div className="flex items-center gap-2 mb-1">
                <FileJson
                  size={14}
                  className={selectedFile === f ? 'text-emerald-500' : 'text-slate-400'}
                />
                <span className="font-medium truncate">{f.replace('.jsonl', '')}</span>
              </div>
              <div className="text-[10px] opacity-60 flex justify-between">
                <span>Session Trace</span>
                <span className="group-hover:opacity-100 opacity-0 transition-opacity">
                  Select →
                </span>
              </div>
            </div>
          ))}
        </div>
      </div>
      <div className="flex-1 overflow-y-auto p-8 bg-white dark:bg-slate-950 custom-scrollbar">
        {selectedFile ? (
          <div className="max-w-4xl mx-auto">
            <div className="flex justify-between items-center mb-8 border-b border-slate-200 dark:border-slate-800 pb-6">
              <div>
                <h2 className="text-2xl font-bold text-slate-800 dark:text-slate-100 flex items-center gap-3">
                  {selectedFile.replace('.jsonl', '')}
                </h2>
                <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
                  Full execution trace for the selected session
                </p>
              </div>
              <div className="text-xs text-slate-400 dark:text-slate-500 bg-slate-100 dark:bg-slate-900 px-3 py-1.5 rounded-full border border-slate-200 dark:border-slate-800 font-mono">
                {events.length} Events
              </div>
            </div>
            {traceTree.length > 0 ? (
              <div className="space-y-1">{traceTree.map((node) => renderSpan(node))}</div>
            ) : (
              <div className="space-y-4">
                {events.map((ev, i) => (
                  <div
                    key={i}
                    className="border border-slate-200 dark:border-slate-800 p-4 rounded bg-slate-50/50 dark:bg-slate-900/50 flex flex-col gap-2"
                  >
                    <div className="flex justify-between">
                      <span className="text-[10px] font-bold text-slate-400 dark:text-slate-500 uppercase tracking-widest">
                        {ev.type}
                      </span>
                      <span className="text-[10px] text-slate-400">
                        {new Date(ev.timestamp).toLocaleTimeString()}
                      </span>
                    </div>
                    <div className="text-sm text-slate-700 dark:text-slate-300 leading-relaxed">
                      {ev.content}
                    </div>
                    {ev.data && Object.keys(ev.data).length > 0 && (
                      <div className="text-[10px] font-mono bg-black/5 p-2 rounded dark:bg-white/5">
                        {JSON.stringify(ev.data)}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>
        ) : (
          <div className="flex h-full flex-col items-center justify-center text-slate-400 dark:text-slate-500 gap-6">
            <div className="w-20 h-20 rounded-2xl bg-slate-50 dark:bg-slate-900 flex items-center justify-center border border-slate-100 dark:border-slate-800 shadow-sm">
              <Layers size={40} className="text-slate-200 dark:text-slate-800" />
            </div>
            <div className="text-center">
              <p className="text-base font-semibold text-slate-600 dark:text-slate-300">
                Welcome to Trace Studio
              </p>
              <p className="text-sm mt-1 max-w-xs mx-auto">
                Select a trace session from the sidebar to explore the hierarchical execution tree
                and performance metrics.
              </p>
            </div>
            <div className="flex gap-4 mt-2">
              <div className="flex flex-col items-center gap-1">
                <div className="p-2 rounded-lg bg-emerald-50 dark:bg-emerald-900/20 text-emerald-500 border border-emerald-100 dark:border-emerald-800">
                  <Activity size={18} />
                </div>
                <span className="text-[10px] font-medium">Performance</span>
              </div>
              <div className="flex flex-col items-center gap-1">
                <div className="p-2 rounded-lg bg-blue-50 dark:bg-blue-900/20 text-blue-500 border border-blue-100 dark:border-blue-800">
                  <Layers size={18} />
                </div>
                <span className="text-[10px] font-medium">Hierarchy</span>
              </div>
              <div className="flex flex-col items-center gap-1">
                <div className="p-2 rounded-lg bg-amber-50 dark:bg-amber-900/20 text-amber-500 border border-amber-100 dark:border-amber-800">
                  <FileJson size={18} />
                </div>
                <span className="text-[10px] font-medium">Raw Data</span>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
