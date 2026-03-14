import React, { useMemo, useRef, useEffect } from 'react'
import { useSystemStore } from '../stores/system'
import { useLogStore } from '../stores/log'
import { eventBusService } from '../services/eventbus'
import { useVirtualizer } from '@tanstack/react-virtual'
import Prism from 'prismjs'
import 'prismjs/themes/prism-tomorrow.css'
import 'prismjs/components/prism-java'
import 'prismjs/components/prism-javascript'
import 'prismjs/components/prism-typescript'
import 'prismjs/components/prism-bash'
import 'prismjs/components/prism-python'
import 'prismjs/components/prism-diff'
import 'prismjs/components/prism-json'
import { cn } from '../lib/utils'

const Inspector: React.FC = () => {
  const systemStore = useSystemStore()
  const logStore = useLogStore()

  const parentRef = useRef<HTMLDivElement>(null)

  const ttyLines = useMemo(() => {
    if (!systemStore.inspectorToolCallId) return []
    const allLines = logStore.activeToolCalls[systemStore.inspectorToolCallId] || []

    if (!systemStore.terminalSearchQuery) return allLines

    try {
      const regex = new RegExp(systemStore.terminalSearchQuery, 'i')
      return allLines.filter((line) => regex.test(line))
    } catch (e) {
      return allLines.filter((line) => line.toLowerCase().includes(systemStore.terminalSearchQuery.toLowerCase()))
    }
  }, [systemStore.inspectorToolCallId, logStore.activeToolCalls, systemStore.terminalSearchQuery])

  const rowVirtualizer = useVirtualizer({
    count: ttyLines.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => 20,
    overscan: 10,
  })

  useEffect(() => {
    if (parentRef.current && systemStore.inspectorMode === 'TERMINAL') {
      const { scrollTop, scrollHeight, clientHeight } = parentRef.current
      const isAtBottom = scrollHeight - scrollTop - clientHeight < 50
      if (isAtBottom && ttyLines.length > 0) {
        rowVirtualizer.scrollToIndex(ttyLines.length - 1)
      }
    }
  }, [ttyLines.length, systemStore.inspectorMode, rowVirtualizer])

  useEffect(() => {
    if (systemStore.inspectFile) {
      eventBusService.send('READ_FILE', { path: systemStore.inspectFile })
    }
  }, [systemStore.inspectFile])

  const currentFile = useMemo(() => {
    if (!systemStore.inspectFile) return null
    return logStore.fileCache[systemStore.inspectFile] || null
  }, [systemStore.inspectFile, logStore.fileCache])

  const highlightedCode = useMemo(() => {
    if (systemStore.inspectorMode === 'CODE' && currentFile) {
      const lang = currentFile.language || 'text'
      const grammar = Prism.languages[lang] || Prism.languages.text
      return Prism.highlight(currentFile.content, grammar, lang)
    }
    return ''
  }, [systemStore.inspectorMode, currentFile])

  const highlightedDiff = useMemo(() => {
    if (systemStore.inspectorMode === 'DIFF' && systemStore.inspectDiff) {
      return Prism.highlight(systemStore.inspectDiff, Prism.languages.diff, 'diff')
    }
    return ''
  }, [systemStore.inspectorMode, systemStore.inspectDiff])

  const copyCode = () => {
    if (systemStore.inspectorMode === 'CODE' && currentFile) {
      navigator.clipboard.writeText(currentFile.content)
    } else if (systemStore.inspectorMode === 'DIFF' && systemStore.inspectDiff) {
      navigator.clipboard.writeText(systemStore.inspectDiff)
    }
  }

  if (!systemStore.isInspectorOpen) return null

  return (
    <aside className="w-[700px] bg-slate-900 border-l border-slate-800 flex flex-col h-full shadow-2xl animate-in slide-in-from-right duration-300 z-20">
      <div className="p-4 border-b border-slate-800 flex justify-between items-center bg-slate-950 shrink-0">
        <div className="flex flex-col flex-1 min-w-0">
          <div className="flex items-center gap-3 mb-1">
            <h3 className="text-xs font-semibold text-slate-200 uppercase tracking-widest">Inspector</h3>
            <div className="flex bg-slate-900 rounded p-0.5 border border-slate-800">
              {(['TERMINAL', 'CODE', 'DIFF'] as const).map((mode) => (
                <button
                  key={mode}
                  onClick={() => systemStore.toggleInspector(systemStore.inspectorToolCallId, mode)}
                  className={cn(
                    'px-2 py-0.5 text-[10px] rounded transition-colors',
                    systemStore.inspectorMode === mode ? 'bg-slate-700 text-white' : 'text-slate-500 hover:text-slate-300'
                  )}
                >
                  {mode === 'TERMINAL' ? 'Terminal' : mode === 'CODE' ? 'Code' : 'Diff'}
                </button>
              ))}
            </div>
          </div>
          <span className="text-[10px] font-mono text-slate-500 truncate block">
            {systemStore.inspectorMode === 'TERMINAL' && `Target: ${systemStore.inspectorToolCallId}`}
            {systemStore.inspectorMode === 'CODE' && `File: ${systemStore.inspectFile}`}
            {systemStore.inspectorMode === 'DIFF' && 'Diff Review'}
          </span>
        </div>

        <div className="flex items-center gap-2">
          {systemStore.inspectorMode === 'CODE' && systemStore.inspectFile && (
            <button
              onClick={() => systemStore.addContextToPrompt(systemStore.inspectFile!)}
              className="text-slate-500 hover:text-emerald-400 transition-colors p-1"
              title="Add to context"
            >
              <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M12 5v14M5 12h14" />
              </svg>
            </button>
          )}
          {((systemStore.inspectorMode === 'CODE' && currentFile) || (systemStore.inspectorMode === 'DIFF' && systemStore.inspectDiff)) && (
            <button onClick={copyCode} className="text-slate-500 hover:text-emerald-400 transition-colors p-1" title="Copy to clipboard">
              <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>
                <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>
              </svg>
            </button>
          )}
          <button onClick={() => systemStore.closeInspector()} className="text-slate-500 hover:text-white transition-colors p-1">
            <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <line x1="18" y1="6" x2="6" y2="18"></line>
              <line x1="6" y1="6" x2="18" y2="18"></line>
            </svg>
          </button>
        </div>
      </div>

      <div className="flex-1 relative bg-black overflow-hidden flex flex-col">
        {systemStore.inspectorMode === 'TERMINAL' && (
          <div className="flex-1 flex flex-col min-h-0">
            <div className="px-4 py-2 border-b border-slate-800 bg-slate-900/50 flex items-center gap-3 shrink-0">
              <div className="relative flex-1">
                <span className="absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-500 text-[10px]">🔍</span>
                <input
                  value={systemStore.terminalSearchQuery}
                  onChange={(e) => useSystemStore.setState({ terminalSearchQuery: e.target.value })}
                  type="text"
                  placeholder="Filter logs (regex supported)..."
                  className="w-full bg-black border border-slate-800 rounded px-8 py-1 text-[11px] text-slate-300 focus:outline-none focus:border-emerald-500/50 placeholder:text-slate-600 transition-all"
                />
                {systemStore.terminalSearchQuery && (
                  <button
                    onClick={() => useSystemStore.setState({ terminalSearchQuery: '' })}
                    className="absolute right-2 top-1/2 -translate-y-1/2 text-slate-500 hover:text-slate-300 text-[10px]"
                  >
                    ✕
                  </button>
                )}
              </div>
              <div className="text-[10px] text-slate-500 font-mono whitespace-nowrap">{ttyLines.length} lines</div>
            </div>

            <div ref={parentRef} className="flex-1 w-full overflow-auto p-4 font-mono text-[11px] leading-tight text-slate-300">
              {ttyLines.length === 0 ? (
                <div className="opacity-30 italic p-4 text-center">
                  {systemStore.terminalSearchQuery ? 'No lines matching search query.' : 'No output recorded for this tool call.'}
                </div>
              ) : (
                <div
                  style={{
                    height: `${rowVirtualizer.getTotalSize()}px`,
                    width: '100%',
                    position: 'relative',
                  }}
                >
                  {rowVirtualizer.getVirtualItems().map((virtualRow) => (
                    <div
                      key={virtualRow.index}
                      style={{
                        position: 'absolute',
                        top: 0,
                        left: 0,
                        width: '100%',
                        height: `${virtualRow.size}px`,
                        transform: `translateY(${virtualRow.start}px)`,
                      }}
                      className="whitespace-pre break-all border-b border-slate-900/30 py-0.5 px-1 hover:bg-slate-900/50"
                    >
                      {ttyLines[virtualRow.index]}
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        )}

        {systemStore.inspectorMode === 'CODE' && (
          <div className="flex-1 flex flex-col min-h-0 bg-[#1d1f21]">
            {!systemStore.inspectFile ? (
              <div className="flex-1 flex flex-col items-center justify-center p-8 text-center">
                <div className="text-3xl mb-4 opacity-20">📄</div>
                <h4 className="text-sm font-medium text-slate-400">No file selected</h4>
                <p className="text-[10px] text-slate-600 mt-2 max-w-xs leading-relaxed">
                  Select a file path from the conversation or tool execution to inspect its content.
                </p>
              </div>
            ) : !currentFile ? (
              <div className="flex-1 flex items-center justify-center">
                <div className="flex items-center gap-2 text-slate-500">
                  <span className="w-4 h-4 border-2 border-slate-500 border-t-transparent rounded-full animate-spin"></span>
                  <span className="text-xs">Reading file...</span>
                </div>
              </div>
            ) : (
              <div className="flex-1 overflow-auto p-4">
                <pre className="!bg-transparent !m-0 !p-0">
                  <code dangerouslySetInnerHTML={{ __html: highlightedCode }} />
                </pre>
              </div>
            )}
          </div>
        )}

        {systemStore.inspectorMode === 'DIFF' && (
          <div className="flex-1 flex flex-col min-h-0 bg-[#1d1f21]">
            {!systemStore.inspectDiff ? (
              <div className="flex-1 flex flex-col items-center justify-center p-8 text-center">
                <div className="text-3xl mb-4 opacity-20">📝</div>
                <h4 className="text-sm font-medium text-slate-400">No diff available</h4>
              </div>
            ) : (
              <div className="flex-1 overflow-auto p-4">
                <pre className="!bg-transparent !m-0 !p-0 text-xs">
                  <code dangerouslySetInnerHTML={{ __html: highlightedDiff }} />
                </pre>
              </div>
            )}
          </div>
        )}
      </div>
    </aside>
  )
}

export default Inspector
