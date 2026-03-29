import React from 'react';
import ReactDOM from 'react-dom/client';
import TraceStudio from './components/TraceStudio';
import './style.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <div className="h-screen w-screen bg-white dark:bg-slate-950 font-sans antialiased text-slate-900 dark:text-slate-200">
      <TraceStudio />
    </div>
  </React.StrictMode>,
);
