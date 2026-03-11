export interface UserClubReputation {
  userId: string
  username: string | null
  firstName: string | null
  lastName: string | null
  avatarUrl: string | null
  reliabilityIndex: number
  promiseFulfillmentPct: number
  spontaneityCount: number
}

export interface AttendanceRecord {
  eventId: string
  eventTitle: string
  eventDatetime: string
  stage1Status: 'going' | 'maybe' | 'not_going' | null
  finalStatus: 'confirmed' | 'waitlisted' | 'declined' | null
  attended: boolean | null
  disputed: boolean
}
