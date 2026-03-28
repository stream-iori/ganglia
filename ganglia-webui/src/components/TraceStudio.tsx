import { useEffect, useState, useMemo } from 'react';

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

  useEffect(() => {
    fetch('http://localhost:8080/api/traces')
      .then((res) => res.json())
      .then((data) => setFiles(data))
      .catch((err) => console.error(err));
  }, []);

  useEffect(() => {
    if (selectedFile) {
      fetch(`http://localhost:8080/api/traces/${selectedFile}`)
        .then((res) => res.json())
        .then((data) => setEvents(data))
        .catch((err) => console.error(err));
    }
  }, [selectedFile]);

  // Build the tree from flat events
  const traceTree = useMemo(() => {
    const nodes: Record<string, SpanNode> = {};
    const rootNodes: SpanNode[] = [];

    // First pass: Create nodes for events that define a span (or are top-level)
    events.forEach((ev) => {
      if (ev.spanId) {
        if (!nodes[ev.spanId]) {
          nodes[ev.spanId] = { event: ev, children: [] };
        } else {
          // Update the event if we saw a child referencing this spanId before the event itself
          nodes[ev.spanId].event = ev;
        }
      }
    });

    // Second pass: Build relationships
    events.forEach((ev) => {
      if (ev.spanId) {
        const node = nodes[ev.spanId];
        if (ev.parentSpanId && nodes[ev.parentSpanId]) {
          if (!nodes[ev.parentSpanId].children.includes(node)) {
            nodes[ev.parentSpanId].children.push(node);
          }
        } else if (!ev.parentSpanId) {
          if (!rootNodes.includes(node)) rootNodes.push(node);
        }
      } else if (!ev.parentSpanId) {
        // Standalone event (not a span, but top-level)
        // For simplicity in this UI, we only show span-based tree or flat list.
        // Let's treat everything as a node if it has no parent.
      }
    });

    return rootNodes;
  }, [events]);

  const renderSpan = (node: SpanNode, depth = 0) => {
    const ev = node.event;
    const duration = ev.data?.durationMs as number | undefined;
    const attempt = ev.data?.attempt as number | undefined;
    const model = ev.data?.model as string | undefined;
    const status = ev.data?.status as string | undefined;

    return (
      <div key={ev.spanId || Math.random()} className="mb-2">
        <div
          className="border border-slate-200 dark:border-slate-700 p-3 rounded-md bg-slate-50 dark:bg-slate-900 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors cursor-pointer group"
          style={{ marginLeft: `${depth * 20}px` }}
        >
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
                {ev.type.replace('_STARTED', '').replace('_FINISHED', '').replace('_RECORDED', '')}
              </span>
              <span className="font-mono text-[10px] text-slate-400 dark:text-slate-500 opacity-0 group-hover:opacity-100 transition-opacity">
                {ev.spanId}
              </span>

              {model && (
                <span className="text-[10px] font-medium text-slate-500 dark:text-slate-400 bg-slate-200/50 dark:bg-slate-800 px-1 rounded">
                  {model}
                </span>
              )}

              {attempt && attempt > 1 && (
                <span className="text-[10px] font-bold text-amber-600 dark:text-amber-400">
                  Retry #{attempt}
                </span>
              )}
            </div>

            <div className="flex items-center gap-3">
              {duration !== undefined && (
                <span className="text-[10px] font-mono text-emerald-600 dark:text-emerald-500 font-bold">
                  {duration}ms
                </span>
              )}
              <span className="text-[10px] text-slate-400 dark:text-slate-500">
                {new Date(ev.timestamp).toLocaleTimeString()}
              </span>
            </div>
          </div>

          <div className="flex items-center gap-2">
            {status === 'failed' && <span className="text-red-500 text-xs">⚠️</span>}
            <div className="text-sm text-slate-700 dark:text-slate-300 font-sans font-medium">
              {ev.type === 'CONTEXT_COMPRESSED'
                ? '🔄 Context Compression Triggered'
                : ev.content === 'context_compression_finished'
                  ? '✅ Context Compression Finished'
                  : ev.content || (ev.type === 'REASONING_STARTED' ? 'Thinking...' : '')}
            </div>
          </div>

          {ev.data && Object.keys(ev.data).length > 0 && (
            <div className="mt-2 text-[10px] text-slate-500 font-mono bg-white/50 dark:bg-black/30 border border-slate-100 dark:border-transparent p-2 rounded overflow-hidden">
              {ev.type === 'CONTEXT_COMPRESSED' || ev.content === 'context_compression_finished' ? (
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
                <div className="max-h-24 overflow-y-auto custom-scrollbar whitespace-pre-wrap">
                  {Object.entries(ev.data).map(([k, v]) => {
                    if (['durationMs', 'attempt', 'model', 'status'].includes(k)) return null;
                    return (
                      <div key={k} className="flex gap-2">
                        <span className="text-slate-400 dark:text-slate-600 shrink-0">{k}:</span>
                        <span className="text-slate-600 dark:text-slate-400 break-all">
                          {typeof v === 'object' ? JSON.stringify(v) : String(v)}
                        </span>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          )}
        </div>
        {node.children.map((child) => renderSpan(child, depth + 1))}
      </div>
    );
  };

  return (
    <div className="flex w-full h-full bg-white dark:bg-slate-950 text-slate-900 dark:text-slate-200">
      <div className="w-64 border-r border-slate-200 dark:border-slate-800 flex flex-col shrink-0 bg-slate-50/50 dark:bg-transparent">
        <div className="p-4 border-b border-slate-200 dark:border-slate-800 font-bold flex items-center gap-2 text-slate-700 dark:text-slate-200">
          <svg
            xmlns="http://www.w3.org/2000/svg"
            width="16"
            height="16"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
          >
            <path d="M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8" />
            <path d="M3 3v5h5" />
            <path d="M3 12a9 9 0 0 0 9 9 9.75 9.75 0 0 0 6.74-2.74L21 16" />
            <path d="M16 21v-5h5" />
          </svg>
          Trace Studio
        </div>
        <div className="overflow-y-auto flex-1 p-2 custom-scrollbar">
          {files.length === 0 && (
            <div className="text-center text-slate-400 dark:text-slate-600 mt-10 text-xs">
              No traces found
            </div>
          )}
          {files.map((f) => (
            <div
              key={f}
              onClick={() => setSelectedFile(f)}
              className={`cursor-pointer p-2 text-xs rounded mb-1 transition-all ${selectedFile === f ? 'bg-emerald-100 dark:bg-emerald-900/40 text-emerald-700 dark:text-emerald-100 border border-emerald-200 dark:border-emerald-800' : 'hover:bg-slate-200 dark:hover:bg-slate-800 text-slate-500 dark:text-slate-400'}`}
            >
              {f.replace('.jsonl', '')}
            </div>
          ))}
        </div>
      </div>
      <div className="flex-1 overflow-y-auto p-8 bg-white dark:bg-slate-950 custom-scrollbar">
        {selectedFile ? (
          <div className="max-w-4xl mx-auto">
            <div className="flex justify-between items-center mb-8 border-b border-slate-200 dark:border-slate-800 pb-4">
              <h2 className="text-xl font-bold text-slate-800 dark:text-slate-100 flex items-center gap-3">
                <span className="text-slate-400 dark:text-slate-500 text-sm font-normal">
                  File:
                </span>{' '}
                {selectedFile}
              </h2>
              <div className="text-xs text-slate-400 dark:text-slate-500 bg-slate-100 dark:bg-slate-900 px-2 py-1 rounded border border-slate-200 dark:border-transparent">
                {events.length} Events
              </div>
            </div>
            {traceTree.length > 0 ? (
              traceTree.map((node) => renderSpan(node))
            ) : (
              <div className="space-y-4">
                {events.map((ev, i) => (
                  <div
                    key={i}
                    className="border border-slate-200 dark:border-slate-800 p-4 rounded bg-slate-50/50 dark:bg-slate-900/50"
                  >
                    <div className="text-[10px] text-slate-400 dark:text-slate-500 mb-1">
                      {ev.type}
                    </div>
                    <div className="text-sm text-slate-700 dark:text-slate-300">{ev.content}</div>
                  </div>
                ))}
              </div>
            )}
          </div>
        ) : (
          <div className="flex h-full flex-col items-center justify-center text-slate-400 dark:text-slate-500 gap-4">
            <div className="w-16 h-16 rounded-full bg-slate-100 dark:bg-slate-900 flex items-center justify-center">
              <svg
                xmlns="http://www.w3.org/2000/svg"
                width="32"
                height="32"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.5"
                strokeLinecap="round"
                strokeLinejoin="round"
                className="text-slate-300 dark:text-slate-700"
              >
                <path d="M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8" />
                <path d="M3 3v5h5" />
                <path d="M3 12a9 9 0 0 0 9 9 9.75 9.75 0 0 0 6.74-2.74L21 16" />
                <path d="M16 21v-5h5" />
              </svg>
            </div>
            <p className="text-sm font-medium">
              Select a trace session from the left to explore the execution tree
            </p>
          </div>
        )}
      </div>
    </div>
  );
}
