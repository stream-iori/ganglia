import React from 'react';
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
  const questions = event.data.questions ?? [];

  const [answers, setAnswers] = React.useState<Record<number, string | string[]>>({});

  const handleAnswerChange = (index: number, value: string | string[]) => {
    setAnswers((prev) => ({ ...prev, [index]: value }));
  };

  const submit = () => {
    // Convert Record to Array, ensuring empty slots are handled if any
    const answersArray = questions.map((_, i) => answers[i] || '');
    eventBusService.send('RESPOND_ASK', {
      askId: event.data.askId,
      answers: answersArray,
    });
    // Optimistically close the modal
    useSystemStore.setState({ activeAskId: null });
  };

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
                The agent needs your input to continue.
              </p>
            </div>
          </div>

          <div className="p-8 overflow-y-auto flex-1 scrollbar-thin scrollbar-thumb-slate-800 space-y-8">
            {questions.map((q, qIdx) => (
              <div key={qIdx} className="space-y-4">
                <div className="flex items-center gap-3">
                  {q.header && (
                    <span className="px-2 py-0.5 bg-slate-800 text-slate-400 text-[10px] font-bold uppercase tracking-wider rounded border border-slate-700">
                      {q.header}
                    </span>
                  )}
                  <h4 className="text-slate-100 text-base font-semibold leading-relaxed">
                    {q.question}
                  </h4>
                </div>

                {q.type === 'text' && (
                  <input
                    type="text"
                    value={(answers[qIdx] as string) || ''}
                    onChange={(e) => handleAnswerChange(qIdx, e.target.value)}
                    placeholder={q.placeholder || 'Type your response...'}
                    className="w-full bg-slate-800 border border-slate-700 rounded-lg px-4 py-3 text-slate-200 focus:outline-none focus:border-emerald-500 focus:ring-1 focus:ring-emerald-500"
                    autoFocus={qIdx === 0}
                  />
                )}

                {q.type === 'yesno' && (
                  <div className="flex gap-4">
                    {['Yes', 'No'].map((opt) => (
                      <button
                        key={opt}
                        onClick={() => handleAnswerChange(qIdx, opt)}
                        className={`px-6 py-2 rounded-lg border transition-all font-semibold ${
                          answers[qIdx] === opt
                            ? 'bg-emerald-600 border-emerald-500 text-white'
                            : 'bg-slate-800 border-slate-700 text-slate-300 hover:border-slate-500'
                        }`}
                      >
                        {opt}
                      </button>
                    ))}
                  </div>
                )}

                {q.type === 'choice' && (q.options ?? []).length > 0 && (
                  <div
                    className={`grid gap-3 ${
                      q.options.length > 2 ? 'grid-cols-1 sm:grid-cols-2' : 'grid-cols-1'
                    }`}
                  >
                    {q.options.map((option) => {
                      const isSelected = q.multiSelect
                        ? (answers[qIdx] as string[])?.includes(option.value)
                        : answers[qIdx] === option.value;

                      return (
                        <button
                          key={option.value}
                          onClick={() => {
                            if (q.multiSelect) {
                              const current = (answers[qIdx] as string[]) || [];
                              const next = current.includes(option.value)
                                ? current.filter((v) => v !== option.value)
                                : [...current, option.value];
                              handleAnswerChange(qIdx, next);
                            } else {
                              handleAnswerChange(qIdx, option.value);
                            }
                          }}
                          className={`flex flex-col items-start p-4 rounded-xl border transition-all text-left group ${
                            isSelected
                              ? 'bg-emerald-600/20 border-emerald-500 shadow-[0_0_15px_rgba(16,185,129,0.1)]'
                              : 'bg-slate-800/50 border-slate-700 hover:border-slate-500'
                          }`}
                        >
                          <span
                            className={`text-sm font-semibold ${
                              isSelected ? 'text-emerald-400' : 'text-slate-200'
                            }`}
                          >
                            {option.label}
                          </span>
                          {option.description && (
                            <span className="text-xs text-slate-400 mt-1 leading-relaxed line-clamp-2">
                              {option.description}
                            </span>
                          )}
                        </button>
                      );
                    })}
                  </div>
                )}
              </div>
            ))}

            {event.data.diffContext && (
              <div className="mt-8 border border-slate-700/50 rounded-lg overflow-hidden bg-slate-900/50">
                <div className="bg-slate-800/30 px-4 py-2 border-b border-slate-700/50 flex items-center justify-between">
                  <span className="text-[10px] font-bold uppercase tracking-wider text-slate-500">
                    Details & Context
                  </span>
                </div>
                <div className="p-5 overflow-x-auto text-sm text-slate-300 leading-relaxed max-h-[250px] overflow-y-auto">
                  {event.data.diffContext.startsWith('```') ? (
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
                                <code
                                  {...props}
                                  dangerouslySetInnerHTML={{ __html: highlighted }}
                                />
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
                      {event.data.diffContext}
                    </ReactMarkdown>
                  ) : (
                    <div className="whitespace-pre-wrap font-mono text-xs opacity-80">
                      {event.data.diffContext}
                    </div>
                  )}
                </div>
              </div>
            )}
          </div>

          <div className="p-6 bg-slate-800/50 border-t border-slate-700/50 flex justify-end gap-4">
            <button
              onClick={submit}
              disabled={questions.some((q, i) => {
                const a = answers[i];
                if (q.type === 'choice' && q.multiSelect) return !a || (a as string[]).length === 0;
                return !a || (typeof a === 'string' && !a.trim());
              })}
              className="px-8 py-3 bg-emerald-600 hover:bg-emerald-500 disabled:opacity-50 disabled:hover:bg-emerald-600 text-white font-bold rounded-xl transition-all shadow-lg shadow-emerald-900/20"
            >
              Confirm Selection
            </button>
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
      <div className="p-4 space-y-2">
        {questions.map((q, i) => (
          <p key={i} className="text-slate-400 text-xs italic">
            &quot;{q.question}&quot;
          </p>
        ))}
        {event.data.diffContext && (
          <div className="text-[10px] font-mono text-slate-500 line-clamp-2 bg-black/30 p-2 rounded">
            {event.data.diffContext}
          </div>
        )}
      </div>
    </div>
  );
};

export default AskUserForm;
