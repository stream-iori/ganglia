import React, { useState, useMemo } from 'react';
import type { FileTreeNode } from '../types';
import { useSystemStore } from '../stores/system';
import { cn } from '../lib/utils';

interface FileTreeItemProps {
  node: FileTreeNode;
  depth: number;
}

const FileTreeItem: React.FC<FileTreeItemProps> = ({ node, depth }) => {
  const systemStore = useSystemStore();
  const [isExpanded, setIsExpanded] = useState(false);

  const isHighlighted = useMemo(() => {
    return node.type === 'file' && systemStore.getModifiedPaths().has(node.path);
  }, [node, systemStore.getModifiedPaths]);

  const toggle = () => {
    if (node.type === 'directory') {
      setIsExpanded(!isExpanded);
    } else {
      systemStore.toggleFileInspector(node.path);
    }
  };

  const addContext = (e: React.MouseEvent) => {
    e.stopPropagation();
    systemStore.addContextToPrompt(node.path);
  };

  return (
    <div className="select-none">
      <div
        onClick={toggle}
        className={cn(
          'flex items-center gap-2 py-1 px-2 hover:bg-slate-800 rounded cursor-pointer transition-colors group relative',
          isHighlighted && 'bg-emerald-900/10 hover:bg-emerald-900/30',
        )}
        style={{ paddingLeft: `${depth * 12 + 8}px` }}
      >
        {isHighlighted && (
          <div className="absolute left-0 top-1/2 -translate-y-1/2 w-0.5 h-3/4 bg-emerald-500 rounded-r"></div>
        )}

        {node.type === 'directory' ? (
          <span
            className={cn(
              'text-slate-500 group-hover:text-slate-300 transition-transform duration-200 text-[10px]',
              isExpanded && 'rotate-90',
            )}
          >
            ▶
          </span>
        ) : (
          <span className="text-slate-600 text-[10px]">📄</span>
        )}

        <span
          className={cn(
            'text-xs truncate flex-1',
            node.type === 'directory' && 'font-medium',
            isHighlighted
              ? 'text-emerald-400 font-medium'
              : 'text-slate-400 group-hover:text-slate-300',
          )}
        >
          {node.name}
        </span>

        {node.type === 'file' && (
          <button
            onClick={addContext}
            className="opacity-0 group-hover:opacity-100 p-0.5 hover:bg-slate-700 rounded text-slate-500 hover:text-emerald-400 transition-all"
            title="Add to context"
          >
            <svg
              xmlns="http://www.w3.org/2000/svg"
              width="12"
              height="12"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2.5"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <path d="M12 5v14M5 12h14" />
            </svg>
          </button>
        )}
      </div>

      {isExpanded && node.children && (
        <div>
          {node.children.map((child) => (
            <FileTreeItem key={child.path} node={child} depth={depth + 1} />
          ))}
        </div>
      )}
    </div>
  );
};

export default FileTreeItem;
