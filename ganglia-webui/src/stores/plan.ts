import { create } from 'zustand'
import type { ToDoList } from '../types'

interface PlanState {
  plan: ToDoList | null
  setPlan: (plan: ToDoList) => void
  clear: () => void
}

export const usePlanStore = create<PlanState>((set) => ({
  plan: null,
  setPlan: (plan) => set({ plan }),
  clear: () => set({ plan: null }),
}))
