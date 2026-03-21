import React, { useMemo } from 'react';
import { eventBusService } from '../services/eventbus';
import { useSystemStore } from '../stores/system';
import type { ServerEvent, AskUserData } from '../types';
import ReactMarkdown from 'react-markdown';
import Prism from 'prismjs';
import 'prismjs/components/prism-diff';

interface AskUserFormProps {
  event: ServerEvent<AskUserData>;
}

const AskUserForm: React.FC<AskUserFormProps> = ({ event }) => {
  const systemStore = useSystemStore();
  const isActive = systemStore.activeAskId === event.data.askId;

  const selectOption = (optionValue: string) => {
    eventBusService.send('RESPOND_ASK', {
      askId: event.data.askId,
      selectedOption: optionValue,
    });
  };

  const [textInput, setTextInput] = React.useState('');

  const submitText = (e: React.FormEvent) => {
    e.preventDefault();
    if (textInput.trim()) {
      selectOption(textInput.trim());
    }
  };

  const diffMarkdown = useMemo(() => {
    if (!event.data.diffContext) return '';
    return `\`\`\`diff\n${event.data.diffContext}\n\`\`\``;
  }, [event.data.diffContext]);

  if (isActive) {
    return (
      <div className="fixed inset-0 bg-slate-950/80 backdrop-blur-md z-50 flex items-center justify-center p-4">
        <div className="max-w-4xl w-full bg-slate-900 border-2 border-amber-500 rounded-2xl overflow-hidden shadow-[0_0_50px_rgba(245,158,11,0.3)] animate-in zoom-in-95 duration-200 max-h-[90vh] flex flex-col">
          <div className="bg-amber-500/10 px-8 py-5 border-b border-amber-500/20 flex items-center gap-4 flex-shrink-0">
            <span className="text-amber-500 text-3xl">⚠️</span>
            <div>
              <h3 className="text-lg font-bold text-amber-500 uppercase tracking-tight">
                Authorization Required
              </h3>
              <p className="text-sm text-slate-400 mt-0.5">
                The agent needs your decision to continue.
              </p>
            </div>
          </div>

          <div className="p-8 overflow-y-auto flex-1 scrollbar-thin scrollbar-thumb-slate-800">
            <p className="text-slate-100 text-base mb-6 leading-relaxed">{event.data.question}</p>

            {event.data.diffContext && (
              <div className="mb-8 border border-slate-700/50 rounded-lg overflow-hidden bg-slate-900">
                <div className="bg-slate-800/50 px-4 py-2 border-b border-slate-700/50 flex items-center justify-between">
                  <span className="text-xs font-mono text-slate-400">Context / Diff</span>
                </div>
                <div className="p-4 overflow-x-auto prose prose-invert prose-sm max-w-none prose-pre:m-0 prose-pre:bg-transparent prose-pre:p-0">
                  <ReactMarkdown
                    components={{
                      code({
                        inline,
                        className,
                        children,
                        ...props
                      }: React.ComponentPropsWithoutRef<'code'> & {
                        inline?: boolean;
                      }) {
                        const match = /language-(\w+)/.exec(className || '');
                        const lang = match ? match[1] : '';
                        if (!inline && lang) {
                          const grammar = Prism.languages[lang] || Prism.languages.text;
                          const highlighted = Prism.highlight(
                            String(children).replace(/\n$/, ''),
                            grammar,
                            lang,
                          );
                          return (
                            <pre className={className}>
                              <code {...props} dangerouslySetInnerHTML={{ __html: highlighted }} />
                            </pre>
                          );
                        }
                        return (
                          <code className={className} {...props}>
                            {children}
                          </code>
                        );
                      },
                    }}
                  >
                    {diffMarkdown}
                  </ReactMarkdown>
                </div>
              </div>
            )}

            {!event.data.options || event.data.options.length === 0 ? (
              <form onSubmit={submitText} className="flex gap-3 mt-auto">
                <input
                  type="text"
                  value={textInput}
                  onChange={(e) => setTextInput(e.target.value)}
                  placeholder="Type your response..."
                  className="flex-1 bg-slate-800 border border-slate-700 rounded-lg px-4 py-3 text-slate-200 focus:outline-none focus:border-emerald-500 focus:ring-1 focus:ring-emerald-500"
                  autoFocus
                />
                <button
                  type="submit"
                  disabled={!textInput.trim()}
                  className="px-6 py-3 bg-emerald-600 hover:bg-emerald-500 disabled:opacity-50 disabled:hover:bg-emerald-600 text-white font-semibold rounded-lg transition-colors"
                >
                  Submit
                </button>
              </form>
            ) : (
              <div
                className={`grid gap-4 mt-auto ${event.data.options.length > 3 ? 'grid-cols-1 sm:grid-cols-2' : 'grid-cols-1'}`}
              >
                {event.data.options.map((option) => (
                  <button
                    key={option.value}
                    onClick={() => selectOption(option.value)}
                    className="flex flex-col items-start p-5 rounded-xl border border-slate-700 bg-slate-800/50 hover:border-emerald-500/50 hover:bg-slate-800 transition-all text-left group"
                  >
                    <span className="text-base font-semibold text-slate-200 group-hover:text-emerald-400">
                      {option.label}
                    </span>
                    <span className="text-sm text-slate-400 mt-1 leading-relaxed">
                      {option.description}
                    </span>
                  </button>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="my-6 bg-slate-900 border-2 border-amber-500/50 rounded-xl overflow-hidden shadow-[0_0_30px_rgba(245,158,11,0.15)] opacity-60">
      <div className="bg-slate-800/50 px-4 py-2 border-b border-slate-700/50 flex items-center gap-2">
        <span className="text-amber-500/50 text-xs">⚠️ Historical Ask</span>
      </div>
      <div className="p-4">
        <p className="text-slate-400 text-xs italic mb-2">&quot;{event.data.question}&quot;</p>
        {event.data.diffContext && (
          <div className="text-[10px] font-mono text-slate-500 line-clamp-3 bg-black/30 p-2 rounded">
            {event.data.diffContext}
          </div>
        )}
      </div>
    </div>
  );
};

export default AskUserForm;
