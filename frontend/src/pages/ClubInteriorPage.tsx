import { useState, useEffect, useCallback } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { clubsApi } from '../api/clubs'
import { eventsApi } from '../api/events'
import type { Club } from '../types/club'
import type { Event, EventStats } from '../types/event'

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

function formatDate(dateStr: string): string {
  const d = new Date(dateStr)
  return d.toLocaleString('ru-RU', {
    day: 'numeric',
    month: 'long',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function EventSkeleton() {
  return (
    <div style={{ padding: '16px', marginBottom: '12px', borderRadius: '12px', background: 'var(--tg-theme-secondary-bg-color, #f5f5f5)' }}>
      <div style={{ height: '18px', width: '70%', background: 'var(--tg-theme-hint-color, #ccc)', borderRadius: '4px', marginBottom: '8px', opacity: 0.4 }} />
      <div style={{ height: '14px', width: '40%', background: 'var(--tg-theme-hint-color, #ccc)', borderRadius: '4px', opacity: 0.3 }} />
    </div>
  )
}

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

type MainTab = 'events' | 'members' | 'profile'
type EventFilter = 'upcoming' | 'past'

export function ClubInteriorPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()

  const [club, setClub] = useState<Club | null>(null)
  const [clubLoading, setClubLoading] = useState(true)
  const [clubError, setClubError] = useState<string | null>(null)

  const [activeTab, setActiveTab] = useState<MainTab>('events')
  const [eventFilter, setEventFilter] = useState<EventFilter>('upcoming')

  const [events, setEvents] = useState<Event[]>([])
  const [eventsLoading, setEventsLoading] = useState(true)
  const [eventsError, setEventsError] = useState<string | null>(null)

  // stats map: eventId -> EventStats
  const [statsMap, setStatsMap] = useState<Record<string, EventStats>>({})
  // my votes map: eventId -> vote
  const [myVotes, setMyVotes] = useState<Record<string, 'going' | 'maybe' | 'not_going'>>({})
  const [votingId, setVotingId] = useState<string | null>(null)

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
      // Fetch stats for each event in parallel
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

  useEffect(() => { fetchClub() }, [fetchClub])
  useEffect(() => { if (activeTab === 'events') fetchEvents() }, [activeTab, fetchEvents])

  const handleVote = async (eventId: string, status: 'going' | 'maybe' | 'not_going') => {
    if (votingId) return
    const prevVote = myVotes[eventId]
    const prevStats = statsMap[eventId]

    // Optimistic update
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
      // Rollback on error
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
        {activeTab === 'events' && (
          <>
            {/* Event sub-tabs */}
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
              <>
                <EventSkeleton />
                <EventSkeleton />
                <EventSkeleton />
              </>
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

        {activeTab === 'members' && (
          <div style={{ textAlign: 'center', padding: '48px 16px' }}>
            <div style={{ fontSize: '48px', marginBottom: '12px' }}>{'\ud83d\udc65'}</div>
            <div style={{ fontSize: '16px', fontWeight: 600 }}>Участники</div>
            <div style={{ fontSize: '14px', color: 'var(--tg-theme-hint-color, #888)', marginTop: '8px' }}>Скоро</div>
          </div>
        )}

        {activeTab === 'profile' && (
          <div style={{ textAlign: 'center', padding: '48px 16px' }}>
            <div style={{ fontSize: '48px', marginBottom: '12px' }}>{'\ud83d\udc64'}</div>
            <div style={{ fontSize: '16px', fontWeight: 600 }}>Мой профиль</div>
            <div style={{ fontSize: '14px', color: 'var(--tg-theme-hint-color, #888)', marginTop: '8px' }}>Скоро</div>
          </div>
        )}
      </div>
    </div>
  )
}
