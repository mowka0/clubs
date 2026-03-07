import { create } from 'zustand'
import type { Club } from '../types/club'

interface ClubsState {
  clubs: Club[]
  isLoading: boolean
  hasMore: boolean
  setClubs: (clubs: Club[]) => void
  appendClubs: (clubs: Club[]) => void
  setLoading: (loading: boolean) => void
  setHasMore: (hasMore: boolean) => void
  reset: () => void
}

export const useClubsStore = create<ClubsState>((set) => ({
  clubs: [],
  isLoading: false,
  hasMore: true,
  setClubs: (clubs) => set({ clubs }),
  appendClubs: (newClubs) => set((state) => ({ clubs: [...state.clubs, ...newClubs] })),
  setLoading: (loading) => set({ isLoading: loading }),
  setHasMore: (hasMore) => set({ hasMore }),
  reset: () => set({ clubs: [], isLoading: false, hasMore: true }),
}))
