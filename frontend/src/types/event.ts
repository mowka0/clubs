export interface Event {
  id: string
  clubId: string
  title: string
  description: string | null
  eventDatetime: string
  location: string | null
  participantLimit: number
  confirmedCount: number
  status: 'upcoming' | 'stage_1' | 'stage_2' | 'completed' | 'cancelled'
  votingOpensDaysBefore: number
  stage2Triggered: boolean
  attendanceFinalized: boolean
  createdAt: string
}

export interface EventStats {
  going: number
  maybe: number
  notGoing: number
  confirmed: number
  limit: number
}

export interface EventResponse {
  userId: string
  username: string | null
  firstName: string | null
  lastName: string | null
  avatarUrl: string | null
  stage1Status: 'going' | 'maybe' | 'not_going' | null
  finalStatus: 'confirmed' | 'waitlisted' | 'declined' | null
  positionInWaitlist: number | null
}

export interface MyEventStatus {
  stage1Status: 'going' | 'maybe' | 'not_going' | null
  finalStatus: 'confirmed' | 'waitlisted' | 'declined' | null
  positionInWaitlist: number | null
}

export type VoteStatus = 'going' | 'maybe' | 'not_going'
