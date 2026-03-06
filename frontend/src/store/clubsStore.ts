import { create } from 'zustand'

interface Club {
  id: string
  name: string
  description: string
  city: string
  category: string
  accessType: 'open' | 'closed' | 'private'
  memberLimit: number
  currentMemberCount: number
  subscriptionPrice: number
  avatarUrl: string | null
  coverUrl: string | null
  isActive: boolean
  activityRating: number
  createdAt: string
}

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
