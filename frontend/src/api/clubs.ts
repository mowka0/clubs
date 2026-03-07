import { apiClient } from './apiClient'
import type { Club, ClubFilters, ClubMember, PagedClubsResponse } from '../types/club'

function buildQuery(filters: ClubFilters): string {
  const params = new URLSearchParams()
  if (filters.city) params.set('city', filters.city)
  if (filters.category) params.set('category', filters.category)
  if (filters.accessType) params.set('accessType', filters.accessType)
  if (filters.priceMin != null) params.set('priceMin', String(filters.priceMin))
  if (filters.priceMax != null) params.set('priceMax', String(filters.priceMax))
  if (filters.search) params.set('search', filters.search)
  if (filters.sort) params.set('sort', filters.sort)
  if (filters.page != null) params.set('page', String(filters.page))
  if (filters.size != null) params.set('size', String(filters.size))
  const qs = params.toString()
  return qs ? `?${qs}` : ''
}

export const clubsApi = {
  getClubs: (filters: ClubFilters = {}): Promise<PagedClubsResponse> =>
    apiClient.get<PagedClubsResponse>(`/clubs${buildQuery(filters)}`),

  getClub: (id: string): Promise<Club> =>
    apiClient.get<Club>(`/clubs/${id}`),

  getMembers: (clubId: string): Promise<ClubMember[]> =>
    apiClient.get<ClubMember[]>(`/clubs/${clubId}/members`),

  join: (clubId: string): Promise<unknown> =>
    apiClient.post(`/clubs/${clubId}/join`),

  apply: (clubId: string, answerText: string): Promise<unknown> =>
    apiClient.post(`/clubs/${clubId}/apply`, { answerText }),

  getGeoCity: (): Promise<{ city: string | null }> =>
    apiClient.get('/geo/city'),
}
