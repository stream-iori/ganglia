import React from 'react'
import { Sun, Moon } from 'lucide-react'
import { useSystemStore } from '../stores/system'

const ThemeToggle: React.FC = () => {
  const theme = useSystemStore((state) => state.theme)
  const setTheme = useSystemStore((state) => state.setTheme)

  return (
    <button
      onClick={() => setTheme(theme === 'light' ? 'dark' : 'light')}
      className="flex items-center justify-center w-7 h-7 rounded bg-slate-900 border border-slate-800 text-slate-400 hover:text-slate-200 transition-colors focus:outline-none focus:ring-1 focus:ring-emerald-500/50 group"
      title={`Switch to ${theme === 'light' ? 'dark' : 'light'} theme`}
    >
      {theme === 'light' ? (
        <Sun size={14} className="group-hover:text-amber-500 transition-colors" />
      ) : (
        <Moon size={14} className="group-hover:text-blue-400 transition-colors" />
      )}
    </button>
  )
}

export default ThemeToggle
