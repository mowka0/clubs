import { apiClient } from './apiClient'
import type { Club, ClubFilters, ClubMember, PagedClubsResponse } from '../types/club'

export interface ApplicationWithUser {
  id: string
  userId: string
  clubId: string
  answerText: string | null
  status: 'pending' | 'approved' | 'rejected' | 'auto_rejected'
  rejectionReason: string | null
  createdAt: string
  updatedAt: string
  userFirstName: string | null
  userLastName: string | null
  userUsername: string | null
  userAvatarUrl: string | null
}

export interface FinancialStats {
  activeMembers: number
  monthlyRevenueStars: number
  organizerShare: number
  platformShare: number
  nextBillingDate: string | null
}

export interface CreateClubRequest {
  name: string
  city: string
  category: string
  accessType: 'open' | 'closed' | 'private'
  memberLimit: number
  subscriptionPrice: number
  description: string
  rules?: string
  applicationQuestion?: string
  avatarBase64?: string
}

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

  getMyClubs: (): Promise<Club[]> =>
    apiClient.get<Club[]>('/clubs/my'),

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

  createClub: (data: CreateClubRequest): Promise<Club> =>
    apiClient.post<Club>('/clubs', data),

  calculateRevenue: (price: number, limit: number): Promise<{ organizerShare: number; platformShare: number }> =>
    apiClient.get(`/clubs/revenue-calculator?price=${price}&limit=${limit}`),

  getApplications: (clubId: string): Promise<ApplicationWithUser[]> =>
    apiClient.get<ApplicationWithUser[]>(`/clubs/${clubId}/applications`),

  approveApplication: (appId: string): Promise<void> =>
    apiClient.put(`/applications/${appId}/approve`),

  rejectApplication: (appId: string, reason?: string): Promise<void> =>
    apiClient.put(`/applications/${appId}/reject`, { reason: reason ?? '' }),

  getFinancialStats: (clubId: string): Promise<FinancialStats> =>
    apiClient.get<FinancialStats>(`/clubs/${clubId}/finances`),

  getClubByInvite: (code: string): Promise<Club> =>
    apiClient.get<Club>(`/clubs/invite/${code}`),
}
