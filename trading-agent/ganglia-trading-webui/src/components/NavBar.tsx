import { NavLink } from 'react-router-dom';
import { useSystemStore } from '@/stores/system';
import { useSignalStore } from '@/stores/signals';
import { cn } from '@/lib/utils';
import {
  LayoutDashboard,
  GitBranch,
  MessageSquare,
  Database,
  Settings,
  Activity,
  Sun,
  Moon,
  Wifi,
  WifiOff,
} from 'lucide-react';

const navItems = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/pipeline', label: 'Pipeline', icon: GitBranch },
  { to: '/debate', label: 'Debate', icon: MessageSquare },
  { to: '/memory', label: 'Memory', icon: Database },
  { to: '/config', label: 'Config', icon: Settings },
  { to: '/trace', label: 'Trace', icon: Activity },
];

const signalColors: Record<string, string> = {
  BUY: 'bg-emerald-500',
  OVERWEIGHT: 'bg-emerald-400',
  HOLD: 'bg-amber-500',
  UNDERWEIGHT: 'bg-orange-500',
  SELL: 'bg-red-500',
};

export function NavBar() {
  const { connectionStatus, theme, toggleTheme } = useSystemStore();
  const latestSignal = useSignalStore((s) => s.latestSignal);

  return (
    <nav className="border-b border-border bg-card px-4 h-14 flex items-center justify-between">
      <div className="flex items-center gap-1">
        <span className="font-semibold text-foreground mr-4">Trading Agent</span>
        {navItems.map(({ to, label, icon: Icon }) => (
          <NavLink
            key={to}
            to={to}
            end={to === '/'}
            className={({ isActive }) =>
              cn(
                'flex items-center gap-1.5 px-3 py-1.5 rounded-md text-sm transition-colors',
                isActive
                  ? 'bg-accent text-accent-foreground font-medium'
                  : 'text-muted-foreground hover:text-foreground hover:bg-accent/50'
              )
            }
          >
            <Icon className="h-4 w-4" />
            {label}
          </NavLink>
        ))}
      </div>

      <div className="flex items-center gap-3">
        {latestSignal && (
          <span
            className={cn(
              'px-2 py-0.5 rounded text-xs font-medium text-white',
              signalColors[latestSignal.signal] || 'bg-muted'
            )}
          >
            {latestSignal.signal}
          </span>
        )}

        <button
          onClick={toggleTheme}
          className="p-1.5 rounded-md text-muted-foreground hover:text-foreground hover:bg-accent"
          aria-label="Toggle theme"
        >
          {theme === 'dark' ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
        </button>

        <div className="flex items-center gap-1.5 text-xs">
          {connectionStatus === 'connected' ? (
            <Wifi className="h-3.5 w-3.5 text-emerald-500" />
          ) : (
            <WifiOff className="h-3.5 w-3.5 text-destructive" />
          )}
          <span className="text-muted-foreground capitalize">{connectionStatus}</span>
        </div>
      </div>
    </nav>
  );
}
