import React, { useEffect } from 'react'
import { eventBusService } from './services/eventbus'
import Sidebar from './components/Sidebar'
import MainStream from './components/MainStream'
import Inspector from './components/Inspector'

const App: React.FC = () => {
  useEffect(() => {
    eventBusService.connect()
  }, [])

  return (
    <div className="flex h-screen w-screen overflow-hidden bg-slate-950 font-sans antialiased text-slate-200">
      <Sidebar />
      <MainStream />
      <Inspector />
    </div>
  )
}

export default App
