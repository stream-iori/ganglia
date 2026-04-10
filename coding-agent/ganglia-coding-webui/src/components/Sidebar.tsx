import React, { useState } from 'react';
import { useSystemStore } from '../stores/system';
import { useLogStore } from '../stores/log';
import { eventBusService } from '../services/eventbus';
import FileTreeItem from './FileTreeItem';
import { cn } from '../lib/utils';

const Sidebar: React.FC = () => {
  const systemStore = useSystemStore();
  const logStore = useLogStore();
  const [activeTab, setActiveTab] = useState<'FILES' | 'SESSIONS'>('FILES');

  const refreshFiles = () => {
    eventBusService.send('LIST_FILES', {});
  };

  const newSession = () => {
    if (
      window.confirm('Start a new session? Current history will be hidden until you switch back.')
    ) {
      const newId = Math.random().toString(36).substring(2, 10);
      systemStore.switchSession(newId);
    }
  };

  return (
    <aside className="w-64 bg-slate-900 text-slate-300 flex flex-col h-full border-r border-slate-800 shrink-0">
      <div className="p-4 border-b border-slate-800 flex justify-between items-start">
        <div>
          <h1 className="text-xl font-bold text-slate-50 flex items-center gap-2">
            <span className="text-emerald-600 dark:text-emerald-500 font-mono">⚛</span> Ganglia
          </h1>
          <div className="mt-2 text-[10px] flex items-center gap-2 uppercase tracking-tighter font-bold">
            <span
              className={cn(
                'w-1.5 h-1.5 rounded-full',
                systemStore.status === 'CONNECTED' &&
                  'bg-emerald-600 dark:bg-emerald-500 shadow-[0_0_5px_rgba(16,185,129,0.5)]',
                systemStore.status === 'RECONNECTING' && 'bg-amber-500',
                systemStore.status === 'DISCONNECTED' && 'bg-rose-500',
              )}
            ></span>
            <span
              className={systemStore.status === 'CONNECTED' ? 'text-slate-200' : 'text-slate-500'}
            >
              {systemStore.status}
            </span>
          </div>
        </div>

        <button
          onClick={newSession}
          className="text-slate-600 hover:text-emerald-500 transition-colors p-1"
          title="New Session"
        >
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
            <path d="M12 5v14M5 12h14" />
          </svg>
        </button>
      </div>

      <div className="flex border-b border-slate-800 px-2 bg-slate-950/30">
        <button
          onClick={() => setActiveTab('FILES')}
          className={cn(
            'flex-1 py-2 text-[10px] font-bold uppercase tracking-widest transition-colors border-b-2',
            activeTab === 'FILES'
              ? 'border-emerald-600 text-emerald-600 dark:border-emerald-500 dark:text-emerald-500'
              : 'border-transparent text-slate-500 hover:text-slate-300',
          )}
        >
          Files
        </button>
        <button
          onClick={() => setActiveTab('SESSIONS')}
          className={cn(
            'flex-1 py-2 text-[10px] font-bold uppercase tracking-widest transition-colors border-b-2',
            activeTab === 'SESSIONS'
              ? 'border-emerald-600 text-emerald-600 dark:border-emerald-500 dark:text-emerald-500'
              : 'border-transparent text-slate-500 hover:text-slate-300',
          )}
        >
          Sessions
        </button>
      </div>

      <div className="flex-1 overflow-y-auto custom-scrollbar">
        {activeTab === 'FILES' ? (
          <div className="p-4">
            <div className="mb-6">
              <div className="flex items-center justify-between mb-3">
                <h2 className="text-[10px] font-bold uppercase tracking-widest text-slate-500">
                  Workspace
                </h2>
                <button
                  onClick={refreshFiles}
                  className="text-slate-600 hover:text-slate-300 transition-colors"
                  title="Refresh file tree"
                >
                  <svg
                    xmlns="http://www.w3.org/2000/svg"
                    width="12"
                    height="12"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="3"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  >
                    <path d="M21 2v6h-6"></path>
                    <path d="M3 12a9 9 0 0 1 15-6.7L21 8"></path>
                    <path d="M3 22v-6h6"></path>
                    <path d="M21 12a9 9 0 0 1-15 6.7L3 16"></path>
                  </svg>
                </button>
              </div>

              {logStore.fileTree ? (
                <FileTreeItem node={logStore.fileTree} depth={0} />
              ) : (
                <div className="text-[10px] opacity-40 italic px-2">
                  Loading project structure...
                </div>
              )}
            </div>
          </div>
        ) : (
          <div className="p-4">
            <h2 className="text-[10px] font-bold uppercase tracking-widest text-slate-500 mb-4">
              Recent Sessions
            </h2>
            <div className="space-y-2">
              {systemStore.sessionHistory.map((id) => (
                <button
                  key={id}
                  onClick={() => systemStore.switchSession(id)}
                  className={cn(
                    'w-full text-left p-3 rounded border transition-all group flex flex-col gap-1',
                    systemStore.sessionId === id
                      ? 'bg-emerald-50 border-emerald-200 text-emerald-700 dark:bg-emerald-950/20 dark:border-emerald-500/50 dark:text-emerald-400'
                      : 'bg-slate-950/50 border-slate-800 hover:border-slate-700 text-slate-400',
                  )}
                >
                  <div className="flex items-center justify-between">
                    <span className="text-[10px] font-mono">{id}</span>
                    {systemStore.sessionId === id && (
                      <span className="text-[8px] bg-emerald-600 dark:bg-emerald-500 text-white dark:text-black px-1 rounded font-bold">
                        ACTIVE
                      </span>
                    )}
                  </div>
                  <span className="text-[9px] text-slate-600 group-hover:text-slate-500">
                    History synced from backend
                  </span>
                </button>
              ))}
            </div>
          </div>
        )}
      </div>

      <div className="p-4 bg-slate-950 border-t border-slate-800 flex items-center justify-between shrink-0">
        <span className="text-[9px] text-slate-600 font-mono tracking-tighter">0.1.6-WEBUI</span>
        <div className="flex gap-2">
          <div className="w-1.5 h-1.5 rounded-full bg-slate-800"></div>
          <div className="w-1.5 h-1.5 rounded-full bg-slate-800"></div>
        </div>
      </div>
    </aside>
  );
};

export default Sidebar;
