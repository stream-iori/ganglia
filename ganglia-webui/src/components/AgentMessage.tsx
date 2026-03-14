import React, { useCallback } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { useSystemStore } from '../stores/system'
import Prism from 'prismjs'
import 'prismjs/themes/prism-tomorrow.css'
import 'prismjs/components/prism-java'
import 'prismjs/components/prism-javascript'
import 'prismjs/components/prism-typescript'
import 'prismjs/components/prism-bash'
import 'prismjs/components/prism-python'
import 'prismjs/components/prism-diff'
import 'prismjs/components/prism-json'

interface AgentMessageProps {
  content: string
}

const AgentMessage: React.FC<AgentMessageProps> = ({ content }) => {
  const systemStore = useSystemStore()

  const handleContentClick = useCallback(
    (event: React.MouseEvent<HTMLDivElement>) => {
      const target = event.target as HTMLElement
      if (target.tagName === 'CODE' || target.tagName === 'SPAN') {
        const text = target.innerText.trim()
        const pathRegex = /^([a-zA-Z0-9_\-.]+\/)*[a-zA-Z0-9_\-.]+\.[a-z0-9]+$/
        if (pathRegex.test(text)) {
          systemStore.toggleFileInspector(text)
        }
      }
    },
    [systemStore]
  )

  return (
    <div className="space-y-2">
      <div className="flex items-center gap-2 text-emerald-500/70 text-[10px] font-bold uppercase tracking-widest mb-1">
        <span className="w-1.5 h-1.5 bg-emerald-500 rounded-full"></span>
        Agent
      </div>
      <div className="prose prose-invert prose-sm max-w-none text-slate-200 cursor-default" onClick={handleContentClick}>
        {!content ? (
          <div className="text-rose-500 text-[10px] italic opacity-50">[DEBUG] Received empty content for Agent Message</div>
        ) : (
          <ReactMarkdown
            remarkPlugins={[remarkGfm]}
            components={{
              code({ node, inline, className, children, ...props }: any) {
                const match = /language-(\w+)/.exec(className || '')
                const lang = match ? match[1] : ''
                
                if (!inline && lang) {
                  const grammar = Prism.languages[lang] || Prism.languages.text
                  const highlighted = Prism.highlight(String(children).replace(/\n$/, ''), grammar, lang)
                  return (
                    <pre className={className}>
                      <code {...props} dangerouslySetInnerHTML={{ __html: highlighted }} />
                    </pre>
                  )
                }
                return (
                  <code className={className} {...props}>
                    {children}
                  </code>
                )
              }
            }}
          >
            {String(content)}
          </ReactMarkdown>
        )}
      </div>
    </div>
  )
}

export default AgentMessage
