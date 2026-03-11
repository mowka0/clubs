import { useState, useEffect, useCallback, useRef } from 'react'
import { useParams } from 'react-router-dom'
import { eventsApi } from '../api/events'
import type { Event, EventStats, MyEventStatus } from '../types/event'

const STATUS_LABELS: Record<string, string> = {
  upcoming: 'Скоро',
  stage_1: '\ud83d\uddf3\ufe0f Голосование',
  stage_2: '\u23f0 Сбор подтверждений',
  completed: '\u2705 Завершено',
  cancelled: '\u274c Отменено',
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
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function Countdown({ targetDate }: { targetDate: string }) {
  const [timeLeft, setTimeLeft] = useState('')

  useEffect(() => {
    const update = () => {
      const diff = new Date(targetDate).getTime() - Date.now()
      if (diff <= 0) { setTimeLeft('Событие началось'); return }
      const hours = Math.floor(diff / 3600000)
      const minutes = Math.floor((diff % 3600000) / 60000)
      if (hours >= 24) {
        const days = Math.floor(hours / 24)
        setTimeLeft(`${days} дн ${hours % 24} ч`)
      } else {
        setTimeLeft(`${hours} ч ${minutes} мин`)
      }
    }
    update()
    const timer = setInterval(update, 60000)
    return () => clearInterval(timer)
  }, [targetDate])

  return (
    <div style={{
      padding: '12px 16px',
      borderRadius: '12px',
      background: '#FF980022',
      textAlign: 'center',
      marginBottom: '16px',
    }}>
      <div style={{ fontSize: '12px', color: '#FF9800', fontWeight: 600, marginBottom: '4px' }}>ДО СОБЫТИЯ</div>
      <div style={{ fontSize: '24px', fontWeight: 700, color: '#FF9800' }}>{timeLeft}</div>
    </div>
  )
}

export function EventPage() {
  const { id } = useParams<{ id: string }>()

  const [event, setEvent] = useState<Event | null>(null)
  const [stats, setStats] = useState<EventStats | null>(null)
  const [myStatus, setMyStatus] = useState<MyEventStatus | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [voting, setVoting] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const [myVote, setMyVote] = useState<'going' | 'maybe' | 'not_going' | null>(null)

  const pollingRef = useRef<number | null>(null)

  const fetchData = useCallback(async () => {
    if (!id) return
    try {
      const [eventData, statsData] = await Promise.all([
        eventsApi.getEvent(id),
        eventsApi.getEventStats(id),
      ])
      setEvent(eventData)
      setStats(statsData)
      // Try to get my status (may fail if not a member)
      try {
        const statusData = await eventsApi.getMyStatus(id)
        setMyStatus(statusData)
        setMyVote(statusData.stage1Status)
      } catch {
        // Not a member or no response yet
      }
    } catch {
      setError('Не удалось загрузить событие')
    } finally {
      setLoading(false)
    }
  }, [id])

  useEffect(() => {
    fetchData()
  }, [fetchData])

  // Polling every 30s for stage_2 events
  useEffect(() => {
    if (event?.status === 'stage_2') {
      pollingRef.current = window.setInterval(fetchData, 30000)
    }
    return () => {
      if (pollingRef.current) clearInterval(pollingRef.current)
    }
  }, [event?.status, fetchData])

  const handleVote = async (status: 'going' | 'maybe' | 'not_going') => {
    if (!id || voting) return
    const prev = myVote
    const prevStats = stats

    // Optimistic update
    setMyVote(status)
    if (stats) {
      setStats((s) => {
        if (!s) return s
        const n = { ...s }
        if (prev === 'going') n.going = Math.max(0, n.going - 1)
        else if (prev === 'maybe') n.maybe = Math.max(0, n.maybe - 1)
        else if (prev === 'not_going') n.notGoing = Math.max(0, n.notGoing - 1)
        if (status === 'going') n.going++
        else if (status === 'maybe') n.maybe++
        else if (status === 'not_going') n.notGoing++
        return n
      })
    }

    setVoting(true)
    try {
      await eventsApi.vote(id, status)
    } catch {
      setMyVote(prev)
      if (prevStats) setStats(prevStats)
    } finally {
      setVoting(false)
    }
  }

  const handleConfirm = async () => {
    if (!id || confirming) return
    setConfirming(true)
    try {
      const result = await eventsApi.confirm(id)
      setMyStatus((prev) => prev ? {
        ...prev,
        finalStatus: result.finalStatus as 'confirmed' | 'waitlisted' | 'declined',
        positionInWaitlist: result.positionInWaitlist,
      } : null)
      await fetchData()
    } catch {
      // Show error (could use toast)
    } finally {
      setConfirming(false)
    }
  }

  const handleDecline = async () => {
    if (!id) return
    try {
      await eventsApi.decline(id)
      setMyStatus((prev) => prev ? { ...prev, finalStatus: 'declined', positionInWaitlist: null } : null)
    } catch {
      // ignore
    }
  }

  if (loading) {
    return (
      <div style={{ padding: '16px' }}>
        <div style={{ height: '24px', width: '70%', background: '#ccc', borderRadius: '4px', marginBottom: '16px', opacity: 0.4 }} />
        <div style={{ height: '16px', width: '40%', background: '#ccc', borderRadius: '4px', marginBottom: '8px', opacity: 0.3 }} />
        <div style={{ height: '16px', width: '50%', background: '#ccc', borderRadius: '4px', marginBottom: '24px', opacity: 0.3 }} />
        <div style={{ height: '80px', background: '#ccc', borderRadius: '12px', opacity: 0.2 }} />
      </div>
    )
  }

  if (error || !event) {
    return (
      <div style={{ padding: '32px', textAlign: 'center' }}>
        <div style={{ marginBottom: '16px', color: 'var(--tg-theme-hint-color, #888)' }}>{error || 'Событие не найдено'}</div>
        <button onClick={fetchData} style={{ padding: '10px 24px', borderRadius: '8px', background: 'var(--tg-theme-button-color, #2196F3)', color: 'var(--tg-theme-button-text-color, #fff)', border: 'none', cursor: 'pointer' }}>
          Повторить
        </button>
      </div>
    )
  }

  const canVoteStage1 = event.status === 'stage_1'
  const isStage2 = event.status === 'stage_2'
  const needsConfirmation = isStage2 && myStatus && !myStatus.finalStatus && (myStatus.stage1Status === 'going' || myStatus.stage1Status === 'maybe')

  return (
    <div style={{ padding: '16px', paddingBottom: '32px' }}>
      {/* Status badge */}
      <div style={{ marginBottom: '12px' }}>
        <span style={{
          display: 'inline-block',
          fontSize: '12px',
          fontWeight: 600,
          padding: '4px 10px',
          borderRadius: '12px',
          background: STATUS_COLORS[event.status] + '22',
          color: STATUS_COLORS[event.status],
        }}>
          {STATUS_LABELS[event.status]}
        </span>
      </div>

      {/* Title */}
      <h1 style={{ fontSize: '22px', fontWeight: 700, margin: '0 0 12px' }}>{event.title}</h1>

      {/* Date & location */}
      <div style={{ fontSize: '14px', color: 'var(--tg-theme-hint-color, #888)', marginBottom: '4px' }}>
        {'\ud83d\udcc5'} {formatDate(event.eventDatetime)}
      </div>
      {event.location && (
        <div style={{ fontSize: '14px', color: 'var(--tg-theme-hint-color, #888)', marginBottom: '16px' }}>
          {'\ud83d\udccd'} {event.location}
        </div>
      )}

      {/* Description */}
      {event.description && (
        <div style={{ fontSize: '14px', lineHeight: 1.6, marginBottom: '20px', padding: '12px', background: 'var(--tg-theme-secondary-bg-color, #f5f5f5)', borderRadius: '12px' }}>
          {event.description}
        </div>
      )}

      {/* Stage 2: Countdown */}
      {isStage2 && <Countdown targetDate={event.eventDatetime} />}

      {/* Stats */}
      {stats && (
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(3, 1fr)',
          gap: '8px',
          marginBottom: '20px',
        }}>
          {[
            { label: 'Пойдут', value: stats.going, emoji: '\u2705' },
            { label: 'Возможно', value: stats.maybe, emoji: '\ud83e\udd14' },
            { label: 'Не пойдут', value: stats.notGoing, emoji: '\u274c' },
          ].map(({ label, value, emoji }) => (
            <div key={label} style={{ textAlign: 'center', padding: '12px 8px', borderRadius: '12px', background: 'var(--tg-theme-secondary-bg-color, #f5f5f5)' }}>
              <div style={{ fontSize: '20px' }}>{emoji}</div>
              <div style={{ fontSize: '20px', fontWeight: 700 }}>{value}</div>
              <div style={{ fontSize: '11px', color: 'var(--tg-theme-hint-color, #888)' }}>{label}</div>
            </div>
          ))}
        </div>
      )}

      {/* Stage 2 confirmed stats */}
      {isStage2 && stats && (
        <div style={{
          padding: '12px 16px',
          borderRadius: '12px',
          background: '#4CAF5022',
          marginBottom: '20px',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
        }}>
          <span style={{ fontSize: '14px', color: '#4CAF50', fontWeight: 600 }}>Подтвердили участие</span>
          <span style={{ fontSize: '20px', fontWeight: 700, color: '#4CAF50' }}>{stats.confirmed}/{stats.limit}</span>
        </div>
      )}

      {/* Stage 1 voting buttons */}
      {canVoteStage1 && (
        <div>
          <div style={{ fontSize: '13px', fontWeight: 600, marginBottom: '10px', color: 'var(--tg-theme-hint-color, #888)' }}>
            Ваш ответ:
          </div>
          <div style={{ display: 'flex', gap: '8px', marginBottom: '20px' }}>
            {(['going', 'maybe', 'not_going'] as const).map((status) => {
              const labels = { going: '\u2705 Пойду', maybe: '\ud83e\udd14 Возможно', not_going: '\u274c Не пойду' }
              const isActive = myVote === status
              return (
                <button
                  key={status}
                  disabled={voting}
                  onClick={() => handleVote(status)}
                  style={{
                    flex: 1,
                    padding: '10px 4px',
                    fontSize: '12px',
                    fontWeight: isActive ? 700 : 400,
                    borderRadius: '10px',
                    border: isActive ? '2px solid var(--tg-theme-button-color, #2196F3)' : '1px solid var(--tg-theme-hint-color, #ccc)',
                    background: isActive ? 'var(--tg-theme-button-color, #2196F3)' : 'transparent',
                    color: isActive ? 'var(--tg-theme-button-text-color, #fff)' : 'var(--tg-theme-text-color, #000)',
                    cursor: voting ? 'not-allowed' : 'pointer',
                    opacity: voting ? 0.6 : 1,
                  }}
                >
                  {labels[status]}
                </button>
              )
            })}
          </div>
        </div>
      )}

      {/* Stage 2 status */}
      {isStage2 && myStatus && (
        <div style={{ marginBottom: '20px' }}>
          {myStatus.finalStatus === 'confirmed' && (
            <div style={{ padding: '16px', borderRadius: '12px', background: '#4CAF5022', textAlign: 'center' }}>
              <div style={{ fontSize: '32px', marginBottom: '8px' }}>{'\u2705'}</div>
              <div style={{ fontSize: '16px', fontWeight: 700, color: '#4CAF50' }}>Место подтверждено</div>
              <div style={{ fontSize: '13px', color: 'var(--tg-theme-hint-color, #888)', marginTop: '4px' }}>Ждём вас на событии!</div>
              <button
                onClick={handleDecline}
                style={{ marginTop: '12px', padding: '8px 20px', borderRadius: '8px', background: 'none', border: '1px solid #f44336', color: '#f44336', fontSize: '13px', cursor: 'pointer' }}
              >
                Отказаться
              </button>
            </div>
          )}
          {myStatus.finalStatus === 'waitlisted' && (
            <div style={{ padding: '16px', borderRadius: '12px', background: '#FF980022', textAlign: 'center' }}>
              <div style={{ fontSize: '32px', marginBottom: '8px' }}>{'\u23f3'}</div>
              <div style={{ fontSize: '16px', fontWeight: 700, color: '#FF9800' }}>
                Вы в резерве{myStatus.positionInWaitlist ? ` (позиция ${myStatus.positionInWaitlist})` : ''}
              </div>
              <div style={{ fontSize: '13px', color: 'var(--tg-theme-hint-color, #888)', marginTop: '4px' }}>
                Если кто-то откажется — вы получите уведомление
              </div>
            </div>
          )}
          {myStatus.finalStatus === 'declined' && (
            <div style={{ padding: '16px', borderRadius: '12px', background: '#f4433622', textAlign: 'center' }}>
              <div style={{ fontSize: '32px', marginBottom: '8px' }}>{'\u274c'}</div>
              <div style={{ fontSize: '16px', fontWeight: 700, color: '#f44336' }}>Участие отклонено</div>
            </div>
          )}
          {needsConfirmation && (
            <div style={{ padding: '16px', borderRadius: '12px', background: '#FF980022', textAlign: 'center' }}>
              <div style={{ fontSize: '14px', marginBottom: '12px', color: '#FF9800', fontWeight: 600 }}>
                Подтвердите участие в событии!
              </div>
              <div style={{ fontSize: '13px', color: 'var(--tg-theme-hint-color, #888)', marginBottom: '16px' }}>
                Если не подтвердите — ваше место перейдёт следующему
              </div>
              <div style={{ display: 'flex', gap: '8px' }}>
                <button
                  onClick={handleConfirm}
                  disabled={confirming}
                  style={{
                    flex: 1,
                    padding: '12px',
                    borderRadius: '10px',
                    background: 'var(--tg-theme-button-color, #2196F3)',
                    color: 'var(--tg-theme-button-text-color, #fff)',
                    border: 'none',
                    fontSize: '14px',
                    fontWeight: 700,
                    cursor: confirming ? 'not-allowed' : 'pointer',
                    opacity: confirming ? 0.6 : 1,
                  }}
                >
                  {confirming ? 'Подтверждаем...' : '\u2705 Подтверждаю'}
                </button>
                <button
                  onClick={handleDecline}
                  style={{
                    padding: '12px 16px',
                    borderRadius: '10px',
                    background: 'none',
                    border: '1px solid #f44336',
                    color: '#f44336',
                    fontSize: '14px',
                    cursor: 'pointer',
                  }}
                >
                  Отказаться
                </button>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
