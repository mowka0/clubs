import { apiClient } from './apiClient'
import type { Event, EventStats, EventResponse, MyEventStatus } from '../types/event'

export const eventsApi = {
  getClubEvents: (clubId: string, filter: 'upcoming' | 'past' = 'upcoming'): Promise<Event[]> =>
    apiClient.get<Event[]>(`/clubs/${clubId}/events?filter=${filter}`),

  getEvent: (id: string): Promise<Event> =>
    apiClient.get<Event>(`/events/${id}`),

  getEventStats: (id: string): Promise<EventStats> =>
    apiClient.get<EventStats>(`/events/${id}/stats`),

  getEventResponses: (id: string): Promise<EventResponse[]> =>
    apiClient.get<EventResponse[]>(`/events/${id}/responses`),

  vote: (id: string, status: 'going' | 'maybe' | 'not_going'): Promise<void> =>
    apiClient.post(`/events/${id}/vote`, { status }),

  confirm: (id: string): Promise<{ finalStatus: string; positionInWaitlist: number | null }> =>
    apiClient.post(`/events/${id}/confirm`),

  decline: (id: string): Promise<void> =>
    apiClient.post(`/events/${id}/decline`),

  getMyStatus: (id: string): Promise<MyEventStatus> =>
    apiClient.get<MyEventStatus>(`/events/${id}/responses/me`),
}
