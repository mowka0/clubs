import { apiClient } from './apiClient'

export interface MembershipDto {
  id: string
  userId: string
  clubId: string
  role: 'member' | 'organizer'
  status: 'active' | 'cancelled' | 'expired' | 'grace_period'
  joinedAt: string
  subscriptionExpiresAt: string | null
  lockedSubscriptionPrice: number | null
}

export interface ApplicationDto {
  id: string
  userId: string
  clubId: string
  answerText: string | null
  status: 'pending' | 'approved' | 'rejected' | 'auto_rejected'
  rejectionReason: string | null
  createdAt: string
  updatedAt: string
}

export const membershipApi = {
  joinClub: (clubId: string): Promise<MembershipDto> =>
    apiClient.post<MembershipDto>(`/clubs/${clubId}/join`),

  leaveClub: (clubId: string): Promise<void> =>
    apiClient.post(`/clubs/${clubId}/leave`),

  joinByInvite: (code: string): Promise<MembershipDto> =>
    apiClient.post<MembershipDto>(`/clubs/invite/${code}/join`),

  applyToClub: (clubId: string, answerText: string): Promise<ApplicationDto> =>
    apiClient.post<ApplicationDto>(`/clubs/${clubId}/apply`, { answerText }),

  getMyApplications: (): Promise<ApplicationDto[]> =>
    apiClient.get<ApplicationDto[]>('/applications/my'),
}
