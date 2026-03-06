import { create } from 'zustand'

interface Event {
  id: string
  clubId: string
  title: string
  description: string
  eventDatetime: string
  location: string
  participantLimit: number
  confirmedCount: number
  status: 'upcoming' | 'stage_1' | 'stage_2' | 'completed' | 'cancelled'
  votingOpensDaysBefore: number
}

interface EventsState {
  events: Event[]
  isLoading: boolean
  setEvents: (events: Event[]) => void
  setLoading: (loading: boolean) => void
}

export const useEventsStore = create<EventsState>((set) => ({
  events: [],
  isLoading: false,
  setEvents: (events) => set({ events }),
  setLoading: (loading) => set({ isLoading: loading }),
}))
