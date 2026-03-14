import React, { useState } from 'react'
import { cn } from '../lib/utils'

interface ThoughtCardProps {
  content: string
  initiallyExpanded?: boolean
}

const ThoughtCard: React.FC<ThoughtCardProps> = ({ content, initiallyExpanded = false }) => {
  const [isExpanded, setIsExpanded] = useState(initiallyExpanded)

  return (
    <div className="border-l-2 border-slate-700 pl-4 py-1 my-2">
      <button
        onClick={() => setIsExpanded(!isExpanded)}
        className="text-[10px] uppercase tracking-widest text-slate-500 hover:text-slate-300 flex items-center gap-2 transition-colors"
      >
        <span className={cn('transition-transform inline-block', isExpanded && 'rotate-90')}>▶</span>
        Thought Process
      </button>

      {isExpanded && (
        <div className="mt-2 text-sm text-slate-400 italic leading-relaxed animate-in fade-in slide-in-from-top-1 duration-200">
          {content}
        </div>
      )}
    </div>
  )
}

export default ThoughtCard
