import { apiClient } from './apiClient'
import type { UserClubReputation, AttendanceRecord } from '../types/reputation'

export const reputationApi = {
  getClubReputation: (clubId: string): Promise<UserClubReputation[]> =>
    apiClient.get<UserClubReputation[]>(`/clubs/${clubId}/reputation`),

  getMyClubReputation: (clubId: string): Promise<UserClubReputation> =>
    apiClient.get<UserClubReputation>(`/users/me/clubs/${clubId}/reputation`),

  getUserClubReputation: (userId: string, clubId: string): Promise<UserClubReputation> =>
    apiClient.get<UserClubReputation>(`/users/${userId}/clubs/${clubId}/reputation`),

  getMyAttendance: (clubId: string): Promise<AttendanceRecord[]> =>
    apiClient.get<AttendanceRecord[]>(`/clubs/${clubId}/my-attendance`),

  getUserAttendance: (userId: string, clubId: string): Promise<AttendanceRecord[]> =>
    apiClient.get<AttendanceRecord[]>(`/clubs/${clubId}/attendance/${userId}`),

  disputeAttendance: (eventId: string, userId: string): Promise<void> =>
    apiClient.post(`/events/${eventId}/dispute/${userId}`),
}
