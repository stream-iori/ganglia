import { create } from 'zustand';
import type { FactData } from '@/types';

interface MemoryState {
  facts: FactData[];
  selectedFact: FactData | null;
  factDetail: string | null;
  roleFilter: string;
  statusFilter: string;
  loading: boolean;

  setFacts: (facts: FactData[]) => void;
  selectFact: (fact: FactData | null) => void;
  setFactDetail: (detail: string | null) => void;
  setRoleFilter: (role: string) => void;
  setStatusFilter: (status: string) => void;
  setLoading: (loading: boolean) => void;
}

export const useMemoryStore = create<MemoryState>((set) => ({
  facts: [],
  selectedFact: null,
  factDetail: null,
  roleFilter: '',
  statusFilter: '',
  loading: false,

  setFacts: (facts) => set({ facts }),
  selectFact: (fact) => set({ selectedFact: fact, factDetail: null }),
  setFactDetail: (detail) => set({ factDetail: detail }),
  setRoleFilter: (role) => set({ roleFilter: role }),
  setStatusFilter: (status) => set({ statusFilter: status }),
  setLoading: (loading) => set({ loading }),
}));
