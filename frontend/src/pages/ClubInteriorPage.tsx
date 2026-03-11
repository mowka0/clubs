import { useState, useEffect, useCallback } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { clubsApi } from '../api/clubs'
import { eventsApi } from '../api/events'
import { reputationApi } from '../api/reputation'
import { membershipApi, type MembershipDto } from '../api/membership'
import { useAuthStore } from '../store/authStore'
import type { Club, ClubMember } from '../types/club'
import type { Event, EventStats } from '../types/event'
import type { UserClubReputation, AttendanceRecord } from '../types/reputation'

const STATUS_LABELS: Record<string, string> = {
  upcoming: 'Скоро',
  stage_1: 'Голосование',
  stage_2: 'Сбор подтверждений',
  completed: 'Завершено',
  cancelled: 'Отменено',
}

const STATUS_COLORS: Record<string, string> = {
  upcoming: '#8888aa',
  stage_1: '#2196F3',
  stage_2: '#FF9800',
  completed: '#4CAF50',
  cancelled: '#f44336',
}

const MEMBERSHIP_STATUS_LABELS: Record<string, string> = {
  active: 'Активна',
  grace_period: 'Льготный период',
  cancelled: 'Отменена',
  expired: 'Истекла',
}

const MEMBERSHIP_STATUS_COLORS: Record<string, string> = {
  active: '#4CAF50',
  grace_period: '#FF9800',
  cancelled: '#888',
  expired: '#f44336',
}

function formatDate(dateStr: string): string {
  const d = new Date(dateStr)
  return d.toLocaleString('ru-RU', {
    day: 'numeric',
    month: 'long',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function formatDateShort(dateStr: string): string {
  const d = new Date(dateStr)
  return d.toLocaleDateString('ru-RU', { day: 'numeric', month: 'long', year: 'numeric' })
}

function getMemberName(m: { firstName: string | null; lastName: string | null; username: string | null }): string {
  if (m.firstName || m.lastName) return [m.firstName, m.lastName].filter(Boolean).join(' ')
  return m.username ?? 'Пользователь'
}

// ─── Skeletons ────────────────────────────────────────────────────────────────

function EventSkeleton() {
  return (
    <div style={{ padding: '16px', marginBottom: '12px', borderRadius: '12px', background: 'var(--tg-theme-secondary-bg-color, #f5f5f5)' }}>
      <div style={{ height: '18px', width: '70%', background: 'var(--tg-theme-hint-color, #ccc)', borderRadius: '4px', marginBottom: '8px', opacity: 0.4 }} />
      <div style={{ height: '14px', width: '40%', background: 'var(--tg-theme-hint-color, #ccc)', borderRadius: '4px', opacity: 0.3 }} />
    </div>
  )
}

function MemberSkeleton() {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: '12px', padding: '12px 0', borderBottom: '1px solid var(--tg-theme-secondary-bg-color, #f5f5f5)' }}>
      <div style={{ width: '44px', height: '44px', borderRadius: '50%', background: 'var(--tg-theme-hint-color, #ccc)', opacity: 0.3, flexShrink: 0 }} />
      <div style={{ flex: 1 }}>
        <div style={{ height: '15px', width: '60%', background: 'var(--tg-theme-hint-color, #ccc)', borderRadius: '4px', marginBottom: '6px', opacity: 0.4 }} />
        <div style={{ height: '12px', width: '35%', background: 'var(--tg-theme-hint-color, #ccc)', borderRadius: '4px', opacity: 0.3 }} />
      </div>
    </div>
  )
}

// ─── EventCard ────────────────────────────────────────────────────────────────

interface EventCardProps {
  event: Event
  stats: EventStats | null
  myVote: 'going' | 'maybe' | 'not_going' | null
  onVote: (eventId: string, status: 'going' | 'maybe' | 'not_going') => void
  onClick: () => void
  isVoting: boolean
}

function EventCard({ event, stats, myVote, onVote, onClick, isVoting }: EventCardProps) {
  const isPast = event.status === 'completed' || event.status === 'cancelled'
  const canVote = event.status === 'stage_1'

  return (
    <div
      onClick={onClick}
      style={{
        padding: '16px',
        marginBottom: '12px',
        borderRadius: '12px',
        background: 'var(--tg-theme-secondary-bg-color, #f5f5f5)',
        cursor: 'pointer',
        opacity: isPast ? 0.75 : 1,
      }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '8px' }}>
        <span style={{ fontWeight: 600, fontSize: '15px', flex: 1, marginRight: '8px' }}>{event.title}</span>
        <span style={{
          fontSize: '11px',
          fontWeight: 600,
          padding: '2px 8px',
          borderRadius: '10px',
          background: STATUS_COLORS[event.status] + '22',
          color: STATUS_COLORS[event.status],
          whiteSpace: 'nowrap',
        }}>
          {STATUS_LABELS[event.status]}
        </span>
      </div>

      <div style={{ fontSize: '13px', color: 'var(--tg-theme-hint-color, #888)', marginBottom: '4px' }}>
        {'\ud83d\udcc5'} {formatDate(event.eventDatetime)}
      </div>
      {event.location && (
        <div style={{ fontSize: '13px', color: 'var(--tg-theme-hint-color, #888)', marginBottom: '8px' }}>
          {'\ud83d\udccd'} {event.location}
        </div>
      )}

      {stats && (
        <div style={{ fontSize: '12px', color: 'var(--tg-theme-hint-color, #888)', marginBottom: canVote ? '12px' : '0' }}>
          {'\u2705'} {stats.going} пойдут · {'\ud83e\udd14'} {stats.maybe} возможно · {'\u274c'} {stats.notGoing} не пойдут
          {event.status === 'stage_2' && (
            <span> · {'\ud83c\udfaf'} {stats.confirmed}/{stats.limit} подтвердили</span>
          )}
        </div>
      )}

      {canVote && (
        <div onClick={(e) => e.stopPropagation()} style={{ display: 'flex', gap: '8px' }}>
          {(['going', 'maybe', 'not_going'] as const).map((status) => {
            const labels = { going: '\u2705 Пойду', maybe: '\ud83e\udd14 Возможно', not_going: '\u274c Не пойду' }
            const isActive = myVote === status
            return (
              <button
                key={status}
                disabled={isVoting}
                onClick={() => onVote(event.id, status)}
                style={{
                  flex: 1,
                  padding: '6px 4px',
                  fontSize: '11px',
                  fontWeight: isActive ? 700 : 400,
                  borderRadius: '8px',
                  border: isActive ? '2px solid var(--tg-theme-button-color, #2196F3)' : '1px solid var(--tg-theme-hint-color, #ccc)',
                  background: isActive ? 'var(--tg-theme-button-color, #2196F3)' : 'transparent',
                  color: isActive ? 'var(--tg-theme-button-text-color, #fff)' : 'var(--tg-theme-text-color, #000)',
                  cursor: isVoting ? 'not-allowed' : 'pointer',
                  opacity: isVoting ? 0.6 : 1,
                }}
              >
                {labels[status]}
              </button>
            )
          })}
        </div>
      )}
    </div>
  )
}

// ─── AttendanceHistory ────────────────────────────────────────────────────────

interface AttendanceHistoryProps {
  records: AttendanceRecord[]
  isLoading: boolean
  currentUserId: string | null
  onDisputed: () => void
}

function AttendanceHistory({ records, isLoading, currentUserId, onDisputed }: AttendanceHistoryProps) {
  const [disputingId, setDisputingId] = useState<string | null>(null)

  const handleDispute = async (eventId: string) => {
    if (!currentUserId || disputingId) return
    setDisputingId(eventId)
    try {
      await reputationApi.disputeAttendance(eventId, currentUserId)
      onDisputed()
    } catch {
      // ignore
    } finally {
      setDisputingId(null)
    }
  }

  if (isLoading) {
    return (
      <div>
        {[1, 2, 3].map((i) => (
          <div key={i} style={{ padding: '12px 0', borderBottom: '1px solid var(--tg-theme-secondary-bg-color, #f5f5f5)' }}>
            <div style={{ height: '14px', width: '65%', background: 'var(--tg-theme-hint-color, #ccc)', borderRadius: '4px', marginBottom: '6px', opacity: 0.4 }} />
            <div style={{ height: '12px', width: '40%', background: 'var(--tg-theme-hint-color, #ccc)', borderRadius: '4px', opacity: 0.3 }} />
          </div>
        ))}
      </div>
    )
  }

  if (records.length === 0) {
    return (
      <div style={{ textAlign: 'center', padding: '24px 0', color: 'var(--tg-theme-hint-color, #888)', fontSize: '14px' }}>
        Пока нет истории посещений
      </div>
    )
  }

  return (
    <div>
      {records.map((r) => {
        const wasAbsent = r.attended === false && !r.disputed
        return (
          <div key={r.eventId} style={{ padding: '12px 0', borderBottom: '1px solid var(--tg-theme-secondary-bg-color, #f5f5f5)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <div style={{ flex: 1 }}>
                <div style={{ fontSize: '14px', fontWeight: 500, marginBottom: '2px' }}>{r.eventTitle}</div>
                <div style={{ fontSize: '12px', color: 'var(--tg-theme-hint-color, #888)' }}>
                  {formatDateShort(r.eventDatetime)}
                </div>
              </div>
              <div style={{ marginLeft: '12px', textAlign: 'right' }}>
                {r.attended === true && (
                  <span style={{ fontSize: '12px', color: '#4CAF50', fontWeight: 600 }}>Был(а)</span>
                )}
                {r.attended === false && (
                  <span style={{ fontSize: '12px', color: r.disputed ? '#FF9800' : '#f44336', fontWeight: 600 }}>
                    {r.disputed ? 'Оспорено' : 'Не был(а)'}
                  </span>
                )}
                {r.attended === null && r.finalStatus === 'confirmed' && (
                  <span style={{ fontSize: '12px', color: '#888' }}>Ожидает отметки</span>
                )}
                {r.attended === null && r.finalStatus === 'waitlisted' && (
                  <span style={{ fontSize: '12px', color: '#888' }}>В резерве</span>
                )}
                {r.attended === null && r.finalStatus === 'declined' && (
                  <span style={{ fontSize: '12px', color: '#888' }}>Отказался</span>
                )}
              </div>
            </div>
            {wasAbsent && (
              <div style={{ marginTop: '8px', padding: '8px 12px', borderRadius: '8px', background: '#f4433622', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span style={{ fontSize: '12px', color: '#f44336' }}>Отмечен(а) как «Не пришёл»</span>
                <button
                  disabled={disputingId === r.eventId}
                  onClick={() => handleDispute(r.eventId)}
                  style={{
                    padding: '4px 12px',
                    fontSize: '12px',
                    borderRadius: '6px',
                    border: '1px solid #f44336',
                    background: 'transparent',
                    color: '#f44336',
                    cursor: 'pointer',
                    opacity: disputingId === r.eventId ? 0.6 : 1,
                  }}
                >
                  Оспорить
                </button>
              </div>
            )}
          </div>
        )
      })}
    </div>
  )
}

// ─── MemberProfilePanel ───────────────────────────────────────────────────────

interface MemberProfilePanelProps {
  member: ClubMember
  clubId: string
  onClose: () => void
}

function MemberProfilePanel({ member, clubId, onClose }: MemberProfilePanelProps) {
  const [reputation, setReputation] = useState<UserClubReputation | null>(null)
  const [attendance, setAttendance] = useState<AttendanceRecord[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    Promise.allSettled([
      reputationApi.getUserClubReputation(member.userId, clubId),
      reputationApi.getUserAttendance(member.userId, clubId),
    ]).then(([repResult, attResult]) => {
      if (cancelled) return
      if (repResult.status === 'fulfilled') setReputation(repResult.value)
      if (attResult.status === 'fulfilled') setAttendance(attResult.value)
      setLoading(false)
    })
    return () => { cancelled = true }
  }, [member.userId, clubId])

  const name = getMemberName(member)
  const initials = name.slice(0, 2).toUpperCase()

  return (
    <div
      style={{
        position: 'fixed', inset: 0, zIndex: 100,
        background: 'rgba(0,0,0,0.4)',
        display: 'flex', alignItems: 'flex-end',
      }}
      onClick={onClose}
    >
      <div
        style={{
          width: '100%',
          maxHeight: '85vh',
          background: 'var(--tg-theme-bg-color, #fff)',
          borderRadius: '20px 20px 0 0',
          overflow: 'hidden',
          display: 'flex',
          flexDirection: 'column',
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Handle */}
        <div style={{ display: 'flex', justifyContent: 'center', padding: '12px 0 0' }}>
          <div style={{ width: '40px', height: '4px', borderRadius: '2px', background: 'var(--tg-theme-hint-color, #ccc)' }} />
        </div>

        <div style={{ overflowY: 'auto', padding: '16px 20px 32px' }}>
          {/* Avatar + Name */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '16px', marginBottom: '24px' }}>
            {member.avatarUrl ? (
              <img src={member.avatarUrl} alt={name} style={{ width: '64px', height: '64px', borderRadius: '50%', objectFit: 'cover' }} />
            ) : (
              <div style={{ width: '64px', height: '64px', borderRadius: '50%', background: 'var(--tg-theme-button-color, #2196F3)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '24px', fontWeight: 700, color: '#fff' }}>
                {initials}
              </div>
            )}
            <div>
              <div style={{ fontSize: '18px', fontWeight: 700 }}>{name}</div>
              {member.username && (
                <div style={{ fontSize: '13px', color: 'var(--tg-theme-hint-color, #888)' }}>@{member.username}</div>
              )}
              <div style={{ fontSize: '12px', color: 'var(--tg-theme-hint-color, #888)', marginTop: '2px' }}>
                В клубе с {formatDateShort(member.joinedAt)}
              </div>
            </div>
          </div>

          {/* Reputation metrics */}
          {loading ? (
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '12px', marginBottom: '24px' }}>
              {[1, 2, 3].map((i) => (
                <div key={i} style={{ padding: '16px', borderRadius: '12px', background: 'var(--tg-theme-secondary-bg-color, #f5f5f5)', textAlign: 'center' }}>
                  <div style={{ height: '28px', width: '60%', margin: '0 auto 6px', background: 'var(--tg-theme-hint-color, #ccc)', borderRadius: '4px', opacity: 0.4 }} />
                  <div style={{ height: '12px', width: '80%', margin: '0 auto', background: 'var(--tg-theme-hint-color, #ccc)', borderRadius: '4px', opacity: 0.3 }} />
                </div>
              ))}
            </div>
          ) : (
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '12px', marginBottom: '24px' }}>
              <ReputationMetric
                value={reputation?.reliabilityIndex ?? member.reliabilityIndex}
                label="Индекс надёжности"
                color="#2196F3"
              />
              <ReputationMetric
                value={reputation ? Math.round(reputation.promiseFulfillmentPct) : null}
                suffix="%"
                label="Выполнение обещаний"
                color="#4CAF50"
              />
              <ReputationMetric
                value={reputation?.spontaneityCount ?? null}
                label="Спонтанных"
                color="#FF9800"
              />
            </div>
          )}

          {/* Attendance history */}
          <div style={{ fontSize: '15px', fontWeight: 700, marginBottom: '12px' }}>История посещений</div>
          <AttendanceHistory
            records={attendance}
            isLoading={loading}
            currentUserId={null}
            onDisputed={() => {}}
          />
        </div>
      </div>
    </div>
  )
}

function ReputationMetric({ value, label, color, suffix = '' }: { value: number | null; label: string; color: string; suffix?: string }) {
  return (
    <div style={{ padding: '16px 8px', borderRadius: '12px', background: 'var(--tg-theme-secondary-bg-color, #f5f5f5)', textAlign: 'center' }}>
      <div style={{ fontSize: '24px', fontWeight: 700, color, marginBottom: '4px' }}>
        {value !== null ? `${value}${suffix}` : '—'}
      </div>
      <div style={{ fontSize: '11px', color: 'var(--tg-theme-hint-color, #888)', lineHeight: 1.3 }}>{label}</div>
    </div>
  )
}

// ─── Main types ───────────────────────────────────────────────────────────────

type MainTab = 'events' | 'members' | 'profile'
type EventFilter = 'upcoming' | 'past'

// ─── ClubInteriorPage ─────────────────────────────────────────────────────────

export function ClubInteriorPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const currentUser = useAuthStore((s) => s.user)

  const [club, setClub] = useState<Club | null>(null)
  const [clubLoading, setClubLoading] = useState(true)
  const [clubError, setClubError] = useState<string | null>(null)

  const [activeTab, setActiveTab] = useState<MainTab>('events')
  const [eventFilter, setEventFilter] = useState<EventFilter>('upcoming')

  // Events tab
  const [events, setEvents] = useState<Event[]>([])
  const [eventsLoading, setEventsLoading] = useState(true)
  const [eventsError, setEventsError] = useState<string | null>(null)
  const [statsMap, setStatsMap] = useState<Record<string, EventStats>>({})
  const [myVotes, setMyVotes] = useState<Record<string, 'going' | 'maybe' | 'not_going'>>({})
  const [votingId, setVotingId] = useState<string | null>(null)

  // Members tab
  const [members, setMembers] = useState<ClubMember[]>([])
  const [membersLoading, setMembersLoading] = useState(false)
  const [membersError, setMembersError] = useState<string | null>(null)
  const [selectedMember, setSelectedMember] = useState<ClubMember | null>(null)

  // Profile tab
  const [myReputation, setMyReputation] = useState<UserClubReputation | null>(null)
  const [myMembership, setMyMembership] = useState<MembershipDto | null>(null)
  const [myAttendance, setMyAttendance] = useState<AttendanceRecord[]>([])
  const [profileLoading, setProfileLoading] = useState(false)
  const [profileError, setProfileError] = useState<string | null>(null)
  const [cancelConfirm, setCancelConfirm] = useState(false)
  const [cancelling, setCancelling] = useState(false)

  const fetchClub = useCallback(async () => {
    if (!id) return
    setClubLoading(true)
    setClubError(null)
    try {
      const data = await clubsApi.getClub(id)
      setClub(data)
    } catch {
      setClubError('Не удалось загрузить клуб')
    } finally {
      setClubLoading(false)
    }
  }, [id])

  const fetchEvents = useCallback(async () => {
    if (!id) return
    setEventsLoading(true)
    setEventsError(null)
    try {
      const data = await eventsApi.getClubEvents(id, eventFilter)
      setEvents(data)
      const statsResults = await Promise.allSettled(
        data.map((e) => eventsApi.getEventStats(e.id))
      )
      const newStatsMap: Record<string, EventStats> = {}
      statsResults.forEach((result, i) => {
        if (result.status === 'fulfilled') {
          newStatsMap[data[i].id] = result.value
        }
      })
      setStatsMap(newStatsMap)
    } catch {
      setEventsError('Не удалось загрузить события')
    } finally {
      setEventsLoading(false)
    }
  }, [id, eventFilter])

  const fetchMembers = useCallback(async () => {
    if (!id) return
    setMembersLoading(true)
    setMembersError(null)
    try {
      const data = await clubsApi.getMembers(id)
      setMembers(data)
    } catch {
      setMembersError('Не удалось загрузить участников')
    } finally {
      setMembersLoading(false)
    }
  }, [id])

  const fetchProfile = useCallback(async () => {
    if (!id) return
    setProfileLoading(true)
    setProfileError(null)
    try {
      const [repResult, membershipResult, attendanceResult] = await Promise.allSettled([
        reputationApi.getMyClubReputation(id),
        membershipApi.getMyMembership(id),
        reputationApi.getMyAttendance(id),
      ])
      if (repResult.status === 'fulfilled') setMyReputation(repResult.value)
      if (membershipResult.status === 'fulfilled') setMyMembership(membershipResult.value)
      if (attendanceResult.status === 'fulfilled') setMyAttendance(attendanceResult.value)
    } catch {
      setProfileError('Не удалось загрузить профиль')
    } finally {
      setProfileLoading(false)
    }
  }, [id])

  useEffect(() => { fetchClub() }, [fetchClub])

  useEffect(() => {
    if (activeTab === 'events') fetchEvents()
    else if (activeTab === 'members') fetchMembers()
    else if (activeTab === 'profile') fetchProfile()
  }, [activeTab, fetchEvents, fetchMembers, fetchProfile])

  const handleVote = async (eventId: string, status: 'going' | 'maybe' | 'not_going') => {
    if (votingId) return
    const prevVote = myVotes[eventId]
    const prevStats = statsMap[eventId]

    setMyVotes((prev) => ({ ...prev, [eventId]: status }))
    if (prevStats) {
      setStatsMap((prev) => {
        const s = { ...prev[eventId] }
        if (prevVote) {
          if (prevVote === 'going') s.going = Math.max(0, s.going - 1)
          else if (prevVote === 'maybe') s.maybe = Math.max(0, s.maybe - 1)
          else if (prevVote === 'not_going') s.notGoing = Math.max(0, s.notGoing - 1)
        }
        if (status === 'going') s.going += 1
        else if (status === 'maybe') s.maybe += 1
        else if (status === 'not_going') s.notGoing += 1
        return { ...prev, [eventId]: s }
      })
    }

    setVotingId(eventId)
    try {
      await eventsApi.vote(eventId, status)
    } catch {
      setMyVotes((prev) => {
        const n = { ...prev }
        if (prevVote) n[eventId] = prevVote
        else delete n[eventId]
        return n
      })
      if (prevStats) setStatsMap((prev) => ({ ...prev, [eventId]: prevStats }))
    } finally {
      setVotingId(null)
    }
  }

  const handleCancelSubscription = async () => {
    if (!id || cancelling) return
    setCancelling(true)
    try {
      await membershipApi.cancelSubscription(id)
      setMyMembership((prev) => prev ? { ...prev, status: 'cancelled' } : prev)
      setCancelConfirm(false)
    } catch {
      // ignore
    } finally {
      setCancelling(false)
    }
  }

  if (clubLoading) {
    return (
      <div style={{ padding: '16px' }}>
        <div style={{ height: '24px', width: '60%', background: 'var(--tg-theme-hint-color, #ccc)', borderRadius: '4px', marginBottom: '16px', opacity: 0.4 }} />
        <EventSkeleton />
        <EventSkeleton />
        <EventSkeleton />
      </div>
    )
  }

  if (clubError || !club) {
    return (
      <div style={{ padding: '32px', textAlign: 'center' }}>
        <div style={{ marginBottom: '16px', color: 'var(--tg-theme-hint-color, #888)' }}>{clubError || 'Клуб не найден'}</div>
        <button onClick={fetchClub} style={{ padding: '10px 24px', borderRadius: '8px', background: 'var(--tg-theme-button-color, #2196F3)', color: 'var(--tg-theme-button-text-color, #fff)', border: 'none', cursor: 'pointer' }}>
          Повторить
        </button>
      </div>
    )
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100vh', overflow: 'hidden' }}>
      {/* Header */}
      <div style={{ padding: '16px 16px 0', background: 'var(--tg-theme-bg-color, #fff)' }}>
        <h2 style={{ margin: '0 0 12px', fontSize: '18px', fontWeight: 700 }}>{club.name}</h2>

        {/* Main tabs */}
        <div style={{ display: 'flex', borderBottom: '1px solid var(--tg-theme-hint-color, #eee)' }}>
          {([['events', 'События'], ['members', 'Участники'], ['profile', 'Мой профиль']] as [MainTab, string][]).map(([tab, label]) => (
            <button
              key={tab}
              onClick={() => setActiveTab(tab)}
              style={{
                flex: 1,
                padding: '10px 4px',
                fontSize: '13px',
                fontWeight: activeTab === tab ? 700 : 400,
                background: 'none',
                border: 'none',
                borderBottom: activeTab === tab ? '2px solid var(--tg-theme-button-color, #2196F3)' : '2px solid transparent',
                color: activeTab === tab ? 'var(--tg-theme-button-color, #2196F3)' : 'var(--tg-theme-hint-color, #888)',
                cursor: 'pointer',
              }}
            >
              {label}
            </button>
          ))}
        </div>
      </div>

      {/* Content */}
      <div style={{ flex: 1, overflowY: 'auto', padding: '16px' }}>

        {/* ── Events Tab ── */}
        {activeTab === 'events' && (
          <>
            <div style={{ display: 'flex', gap: '8px', marginBottom: '16px' }}>
              {(['upcoming', 'past'] as EventFilter[]).map((f) => (
                <button
                  key={f}
                  onClick={() => setEventFilter(f)}
                  style={{
                    padding: '6px 16px',
                    borderRadius: '16px',
                    border: 'none',
                    fontSize: '13px',
                    fontWeight: eventFilter === f ? 600 : 400,
                    background: eventFilter === f ? 'var(--tg-theme-button-color, #2196F3)' : 'var(--tg-theme-secondary-bg-color, #f5f5f5)',
                    color: eventFilter === f ? 'var(--tg-theme-button-text-color, #fff)' : 'var(--tg-theme-text-color, #000)',
                    cursor: 'pointer',
                  }}
                >
                  {f === 'upcoming' ? 'Предстоящие' : 'Прошедшие'}
                </button>
              ))}
            </div>

            {eventsLoading ? (
              <><EventSkeleton /><EventSkeleton /><EventSkeleton /></>
            ) : eventsError ? (
              <div style={{ textAlign: 'center', padding: '32px' }}>
                <div style={{ color: 'var(--tg-theme-hint-color, #888)', marginBottom: '12px' }}>{eventsError}</div>
                <button onClick={fetchEvents} style={{ padding: '10px 24px', borderRadius: '8px', background: 'var(--tg-theme-button-color, #2196F3)', color: 'var(--tg-theme-button-text-color, #fff)', border: 'none', cursor: 'pointer' }}>
                  Повторить
                </button>
              </div>
            ) : events.length === 0 ? (
              <div style={{ textAlign: 'center', padding: '48px 16px' }}>
                <div style={{ fontSize: '48px', marginBottom: '12px' }}>{'\ud83d\udcc5'}</div>
                <div style={{ fontSize: '16px', fontWeight: 600, marginBottom: '8px' }}>
                  {eventFilter === 'upcoming' ? 'Пока нет предстоящих событий' : 'Нет прошедших событий'}
                </div>
                <div style={{ fontSize: '14px', color: 'var(--tg-theme-hint-color, #888)' }}>
                  {eventFilter === 'upcoming' ? 'Организатор скоро добавит новое событие' : ''}
                </div>
              </div>
            ) : (
              events.map((event) => (
                <EventCard
                  key={event.id}
                  event={event}
                  stats={statsMap[event.id] ?? null}
                  myVote={myVotes[event.id] ?? null}
                  onVote={handleVote}
                  onClick={() => navigate(`/events/${event.id}`)}
                  isVoting={votingId === event.id}
                />
              ))
            )}
          </>
        )}

        {/* ── Members Tab ── */}
        {activeTab === 'members' && (
          <>
            {membersLoading ? (
              <><MemberSkeleton /><MemberSkeleton /><MemberSkeleton /><MemberSkeleton /><MemberSkeleton /></>
            ) : membersError ? (
              <div style={{ textAlign: 'center', padding: '32px' }}>
                <div style={{ color: 'var(--tg-theme-hint-color, #888)', marginBottom: '12px' }}>{membersError}</div>
                <button onClick={fetchMembers} style={{ padding: '10px 24px', borderRadius: '8px', background: 'var(--tg-theme-button-color, #2196F3)', color: 'var(--tg-theme-button-text-color, #fff)', border: 'none', cursor: 'pointer' }}>
                  Повторить
                </button>
              </div>
            ) : members.length === 0 ? (
              <div style={{ textAlign: 'center', padding: '48px 16px' }}>
                <div style={{ fontSize: '48px', marginBottom: '12px' }}>{'\ud83d\udc65'}</div>
                <div style={{ fontSize: '16px', fontWeight: 600 }}>Нет участников</div>
              </div>
            ) : (
              members.map((member) => {
                const name = getMemberName(member)
                const initials = name.slice(0, 2).toUpperCase()
                return (
                  <div
                    key={member.userId}
                    onClick={() => setSelectedMember(member)}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: '12px',
                      padding: '12px 0',
                      borderBottom: '1px solid var(--tg-theme-secondary-bg-color, #f5f5f5)',
                      cursor: 'pointer',
                    }}
                  >
                    {member.avatarUrl ? (
                      <img src={member.avatarUrl} alt={name} style={{ width: '44px', height: '44px', borderRadius: '50%', objectFit: 'cover', flexShrink: 0 }} />
                    ) : (
                      <div style={{ width: '44px', height: '44px', borderRadius: '50%', background: 'var(--tg-theme-button-color, #2196F3)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '16px', fontWeight: 700, color: '#fff', flexShrink: 0 }}>
                        {initials}
                      </div>
                    )}
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ fontSize: '15px', fontWeight: 500, marginBottom: '2px', display: 'flex', alignItems: 'center', gap: '6px' }}>
                        <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{name}</span>
                        {member.role === 'organizer' && (
                          <span style={{ fontSize: '11px', padding: '1px 6px', borderRadius: '6px', background: '#FF980022', color: '#FF9800', flexShrink: 0 }}>Организатор</span>
                        )}
                      </div>
                      <div style={{ fontSize: '12px', color: 'var(--tg-theme-hint-color, #888)' }}>
                        Надёжность: <strong style={{ color: member.reliabilityIndex >= 0 ? '#4CAF50' : '#f44336' }}>{member.reliabilityIndex}</strong>
                      </div>
                    </div>
                    <div style={{ color: 'var(--tg-theme-hint-color, #ccc)', fontSize: '18px' }}>›</div>
                  </div>
                )
              })
            )}
          </>
        )}

        {/* ── My Profile Tab ── */}
        {activeTab === 'profile' && (
          <>
            {profileLoading ? (
              <>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '12px', marginBottom: '24px' }}>
                  {[1, 2, 3].map((i) => (
                    <div key={i} style={{ padding: '16px 8px', borderRadius: '12px', background: 'var(--tg-theme-secondary-bg-color, #f5f5f5)', textAlign: 'center' }}>
                      <div style={{ height: '28px', width: '60%', margin: '0 auto 6px', background: 'var(--tg-theme-hint-color, #ccc)', borderRadius: '4px', opacity: 0.4 }} />
                      <div style={{ height: '12px', width: '80%', margin: '0 auto', background: 'var(--tg-theme-hint-color, #ccc)', borderRadius: '4px', opacity: 0.3 }} />
                    </div>
                  ))}
                </div>
                <div style={{ height: '80px', borderRadius: '12px', background: 'var(--tg-theme-secondary-bg-color, #f5f5f5)', marginBottom: '24px', opacity: 0.4 }} />
              </>
            ) : profileError ? (
              <div style={{ textAlign: 'center', padding: '32px' }}>
                <div style={{ color: 'var(--tg-theme-hint-color, #888)', marginBottom: '12px' }}>{profileError}</div>
                <button onClick={fetchProfile} style={{ padding: '10px 24px', borderRadius: '8px', background: 'var(--tg-theme-button-color, #2196F3)', color: 'var(--tg-theme-button-text-color, #fff)', border: 'none', cursor: 'pointer' }}>
                  Повторить
                </button>
              </div>
            ) : (
              <>
                {/* Reputation metrics */}
                <div style={{ marginBottom: '8px', fontSize: '13px', fontWeight: 600, color: 'var(--tg-theme-hint-color, #888)', textTransform: 'uppercase', letterSpacing: '0.5px' }}>
                  Репутация в клубе
                </div>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '12px', marginBottom: '24px' }}>
                  <ReputationMetric
                    value={myReputation?.reliabilityIndex ?? 0}
                    label="Индекс надёжности"
                    color="#2196F3"
                  />
                  <ReputationMetric
                    value={myReputation ? Math.round(myReputation.promiseFulfillmentPct) : 0}
                    suffix="%"
                    label="Выполнение обещаний"
                    color="#4CAF50"
                  />
                  <ReputationMetric
                    value={myReputation?.spontaneityCount ?? 0}
                    label="Спонтанных"
                    color="#FF9800"
                  />
                </div>

                {/* Subscription block */}
                {myMembership && (
                  <div style={{ marginBottom: '24px', padding: '16px', borderRadius: '12px', background: 'var(--tg-theme-secondary-bg-color, #f5f5f5)' }}>
                    <div style={{ fontSize: '13px', fontWeight: 600, color: 'var(--tg-theme-hint-color, #888)', textTransform: 'uppercase', letterSpacing: '0.5px', marginBottom: '12px' }}>
                      Подписка
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                      <span style={{ fontSize: '14px' }}>Статус</span>
                      <span style={{ fontSize: '14px', fontWeight: 600, color: MEMBERSHIP_STATUS_COLORS[myMembership.status] ?? '#888' }}>
                        {MEMBERSHIP_STATUS_LABELS[myMembership.status] ?? myMembership.status}
                      </span>
                    </div>
                    {myMembership.subscriptionExpiresAt && (
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
                        <span style={{ fontSize: '14px' }}>
                          {myMembership.status === 'cancelled' ? 'Доступ до' : 'Следующее списание'}
                        </span>
                        <span style={{ fontSize: '14px', fontWeight: 500 }}>
                          {formatDateShort(myMembership.subscriptionExpiresAt)}
                        </span>
                      </div>
                    )}
                    {myMembership.lockedSubscriptionPrice != null && (
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
                        <span style={{ fontSize: '14px' }}>Цена</span>
                        <span style={{ fontSize: '14px', fontWeight: 500 }}>{myMembership.lockedSubscriptionPrice} Stars/мес</span>
                      </div>
                    )}
                    {myMembership.status === 'active' && (
                      <>
                        {cancelConfirm ? (
                          <div style={{ padding: '12px', borderRadius: '8px', background: '#f4433611' }}>
                            <div style={{ fontSize: '13px', marginBottom: '12px', color: '#f44336' }}>
                              После отмены вы сохраните доступ до конца оплаченного периода.
                            </div>
                            <div style={{ display: 'flex', gap: '8px' }}>
                              <button
                                onClick={() => setCancelConfirm(false)}
                                style={{ flex: 1, padding: '8px', borderRadius: '8px', border: '1px solid var(--tg-theme-hint-color, #ccc)', background: 'transparent', fontSize: '13px', cursor: 'pointer' }}
                              >
                                Назад
                              </button>
                              <button
                                disabled={cancelling}
                                onClick={handleCancelSubscription}
                                style={{ flex: 1, padding: '8px', borderRadius: '8px', border: 'none', background: '#f44336', color: '#fff', fontSize: '13px', fontWeight: 600, cursor: 'pointer', opacity: cancelling ? 0.6 : 1 }}
                              >
                                {cancelling ? 'Отмена...' : 'Подтвердить'}
                              </button>
                            </div>
                          </div>
                        ) : (
                          <button
                            onClick={() => setCancelConfirm(true)}
                            style={{ width: '100%', padding: '10px', borderRadius: '8px', border: '1px solid #f44336', background: 'transparent', color: '#f44336', fontSize: '14px', cursor: 'pointer' }}
                          >
                            Отменить подписку
                          </button>
                        )}
                      </>
                    )}
                  </div>
                )}

                {/* Attendance history */}
                <div style={{ fontSize: '15px', fontWeight: 700, marginBottom: '12px' }}>История посещений</div>
                <AttendanceHistory
                  records={myAttendance}
                  isLoading={false}
                  currentUserId={currentUser?.id ?? null}
                  onDisputed={fetchProfile}
                />
              </>
            )}
          </>
        )}
      </div>

      {/* Member profile panel */}
      {selectedMember && (
        <MemberProfilePanel
          member={selectedMember}
          clubId={id ?? ''}
          onClose={() => setSelectedMember(null)}
        />
      )}

      {/* Cancel confirm overlay (covers screen when active) */}
    </div>
  )
}
