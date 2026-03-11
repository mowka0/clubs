import { useState, useEffect, useCallback } from 'react'
import type { Club, ClubMember } from '../types/club'
import type { Event, EventStats, EventResponse } from '../types/event'
import { eventsApi, type CreateEventRequest } from '../api/events'
import { clubsApi, type ApplicationWithUser, type FinancialStats } from '../api/clubs'

// ─── Helpers ──────────────────────────────────────────────────────────────────

function userName(u: { firstName?: string | null; lastName?: string | null; username?: string | null }) {
  const full = [u.firstName, u.lastName].filter(Boolean).join(' ')
  return full || (u.username ? `@${u.username}` : 'Участник')
}

function fmtDate(d: string) {
  return new Intl.DateTimeFormat('ru-RU', { day: 'numeric', month: 'long', year: 'numeric' }).format(new Date(d))
}

function fmtDatetime(d: string) {
  return new Intl.DateTimeFormat('ru-RU', {
    day: 'numeric', month: 'long', hour: '2-digit', minute: '2-digit',
  }).format(new Date(d))
}

function countdown48h(createdAt: string): string {
  const deadline = new Date(createdAt).getTime() + 48 * 3600 * 1000
  const remaining = deadline - Date.now()
  if (remaining <= 0) return 'Истекло'
  const hours = Math.floor(remaining / 3600000)
  const mins = Math.floor((remaining % 3600000) / 60000)
  return `${hours}ч ${mins}м`
}

// ─── Shared UI ────────────────────────────────────────────────────────────────

const retryBtnStyle: React.CSSProperties = {
  padding: '10px 20px', borderRadius: '8px',
  border: '1.5px solid var(--tg-theme-button-color, #2196F3)',
  background: 'transparent', color: 'var(--tg-theme-button-color, #2196F3)',
  cursor: 'pointer', fontSize: '14px',
}

const actionBtnStyle: React.CSSProperties = {
  padding: '10px 16px', borderRadius: '8px', border: 'none',
  color: '#fff', cursor: 'pointer', fontSize: '14px', fontWeight: 600,
}

const backBtnStyle: React.CSSProperties = {
  background: 'none', border: 'none', fontSize: '20px', cursor: 'pointer',
  padding: '4px 8px 4px 0', color: 'var(--tg-theme-button-color, #2196F3)',
}

const labelStyle: React.CSSProperties = {
  fontSize: '13px', color: 'var(--tg-theme-hint-color, #888)', fontWeight: 500,
  display: 'block', marginBottom: '6px',
}

const inputBaseStyle: React.CSSProperties = {
  width: '100%', padding: '12px 14px', borderRadius: '10px',
  border: '1.5px solid var(--tg-theme-secondary-bg-color, #e0e0e0)',
  background: 'var(--tg-theme-secondary-bg-color, #f5f5f5)',
  color: 'var(--tg-theme-text-color, #000)', fontSize: '15px',
  outline: 'none', boxSizing: 'border-box',
}

function Skeleton({ n = 3 }: { n?: number }) {
  return (
    <>
      {Array.from({ length: n }).map((_, i) => (
        <div key={i} style={{
          padding: '14px 16px', borderRadius: '12px',
          background: 'var(--tg-theme-secondary-bg-color, #f5f5f5)', marginBottom: '10px',
        }}>
          <div style={{ height: '15px', width: `${50 + i * 15}%`, background: 'var(--tg-theme-hint-color, #ccc)', borderRadius: '4px', opacity: 0.4, marginBottom: '8px' }} />
          <div style={{ height: '12px', width: '35%', background: 'var(--tg-theme-hint-color, #ccc)', borderRadius: '4px', opacity: 0.3 }} />
        </div>
      ))}
    </>
  )
}

function Empty({ icon, text, sub }: { icon: string; text: string; sub?: string }) {
  return (
    <div style={{ textAlign: 'center', padding: '48px 16px', color: 'var(--tg-theme-hint-color, #888)' }}>
      <div style={{ fontSize: '40px', marginBottom: '12px' }}>{icon}</div>
      <div style={{ fontWeight: 600, fontSize: '16px', color: 'var(--tg-theme-text-color, #222)', marginBottom: sub ? '8px' : 0 }}>{text}</div>
      {sub && <div style={{ fontSize: '14px' }}>{sub}</div>}
    </div>
  )
}

function Avatar({ name, url, size = 40 }: { name: string; url?: string | null; size?: number }) {
  if (url) {
    return <img src={url} alt="" style={{ width: size, height: size, borderRadius: '50%', objectFit: 'cover', flexShrink: 0 }} />
  }
  return (
    <div style={{
      width: size, height: size, borderRadius: '50%', flexShrink: 0,
      background: 'var(--tg-theme-button-color, #2196F3)',
      color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center',
      fontSize: size * 0.4, fontWeight: 700,
    }}>
      {name.charAt(0).toUpperCase()}
    </div>
  )
}

function SectionHeader({ text }: { text: string }) {
  return (
    <div style={{
      fontSize: '12px', fontWeight: 700, color: 'var(--tg-theme-hint-color, #888)',
      textTransform: 'uppercase', letterSpacing: '0.5px', margin: '16px 0 8px',
    }}>
      {text}
    </div>
  )
}

// ─── Members Tab ──────────────────────────────────────────────────────────────

function MembersTab({ clubId }: { clubId: string }) {
  const [members, setMembers] = useState<ClubMember[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setMembers(await clubsApi.getMembers(clubId))
    } catch {
      setError('Не удалось загрузить участников')
    } finally {
      setLoading(false)
    }
  }, [clubId])

  useEffect(() => { load() }, [load])

  if (loading) return <Skeleton n={5} />
  if (error) return (
    <div style={{ textAlign: 'center', padding: '32px 0' }}>
      <div style={{ color: '#f44336', fontSize: '14px', marginBottom: '12px' }}>{error}</div>
      <button onClick={load} style={retryBtnStyle}>Повторить</button>
    </div>
  )
  if (members.length === 0) return <Empty icon="👥" text="Нет участников" sub="Пригласите первых участников в клуб" />

  return (
    <div>
      {members.map(m => {
        const name = userName(m)
        return (
          <div key={m.userId} style={{
            display: 'flex', alignItems: 'center', gap: '12px',
            padding: '12px 16px', borderRadius: '12px',
            background: 'var(--tg-theme-secondary-bg-color, #f5f5f5)', marginBottom: '10px',
          }}>
            <Avatar name={name} url={m.avatarUrl} />
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontWeight: 600, fontSize: '15px', marginBottom: '2px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {name}
                {m.role === 'organizer' && (
                  <span style={{ marginLeft: '6px', fontSize: '11px', color: 'var(--tg-theme-button-color, #2196F3)', fontWeight: 500 }}>
                    Организатор
                  </span>
                )}
              </div>
              <div style={{ fontSize: '12px', color: 'var(--tg-theme-hint-color, #888)' }}>
                Вступил {fmtDate(m.joinedAt)} · Индекс: {m.reliabilityIndex}
              </div>
            </div>
          </div>
        )
      })}
    </div>
  )
}

// ─── Applications Tab ─────────────────────────────────────────────────────────

function ApplicationsTab({ clubId }: { clubId: string }) {
  const [apps, setApps] = useState<ApplicationWithUser[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [actionId, setActionId] = useState<string | null>(null)
  const [rejectId, setRejectId] = useState<string | null>(null)
  const [rejectReason, setRejectReason] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const all = await clubsApi.getApplications(clubId)
      setApps(all.filter(a => a.status === 'pending'))
    } catch {
      setError('Не удалось загрузить заявки')
    } finally {
      setLoading(false)
    }
  }, [clubId])

  useEffect(() => { load() }, [load])

  async function approve(id: string) {
    setActionId(id)
    try {
      await clubsApi.approveApplication(id)
      setApps(prev => prev.filter(a => a.id !== id))
    } catch {
      alert('Не удалось одобрить заявку')
    } finally {
      setActionId(null)
    }
  }

  async function reject(id: string) {
    setActionId(id)
    try {
      await clubsApi.rejectApplication(id, rejectReason || undefined)
      setApps(prev => prev.filter(a => a.id !== id))
      setRejectId(null)
      setRejectReason('')
    } catch {
      alert('Не удалось отклонить заявку')
    } finally {
      setActionId(null)
    }
  }

  if (loading) return <Skeleton n={3} />
  if (error) return (
    <div style={{ textAlign: 'center', padding: '32px 0' }}>
      <div style={{ color: '#f44336', fontSize: '14px', marginBottom: '12px' }}>{error}</div>
      <button onClick={load} style={retryBtnStyle}>Повторить</button>
    </div>
  )
  if (apps.length === 0) return <Empty icon="📬" text="Нет входящих заявок" sub="Все заявки обработаны" />

  return (
    <div>
      {apps.map(app => {
        const name = userName({
          firstName: app.userFirstName,
          lastName: app.userLastName,
          username: app.userUsername,
        })
        const cd = countdown48h(app.createdAt)
        const isExpired = cd === 'Истекло'

        return (
          <div key={app.id} style={{
            padding: '16px', borderRadius: '12px',
            background: 'var(--tg-theme-secondary-bg-color, #f5f5f5)', marginBottom: '12px',
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '10px' }}>
              <Avatar name={name} url={app.userAvatarUrl} size={36} />
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontWeight: 600, fontSize: '15px' }}>{name}</div>
                <div style={{ fontSize: '12px', color: isExpired ? '#f44336' : 'var(--tg-theme-hint-color, #888)' }}>
                  Авто-отклонение через: {cd}
                </div>
              </div>
            </div>

            {app.answerText && (
              <div style={{
                padding: '10px 12px', borderRadius: '8px', background: 'rgba(0,0,0,0.05)',
                fontSize: '14px', marginBottom: '12px', fontStyle: 'italic',
              }}>
                "{app.answerText}"
              </div>
            )}

            {rejectId === app.id ? (
              <div>
                <input
                  value={rejectReason}
                  onChange={e => setRejectReason(e.target.value)}
                  placeholder="Причина отклонения (необязательно)"
                  style={{
                    width: '100%', padding: '10px 12px', borderRadius: '8px',
                    border: '1.5px solid var(--tg-theme-secondary-bg-color, #e0e0e0)',
                    background: 'var(--tg-theme-bg-color, #fff)',
                    fontSize: '14px', marginBottom: '10px', boxSizing: 'border-box',
                    color: 'var(--tg-theme-text-color, #000)', outline: 'none',
                  }}
                />
                <div style={{ display: 'flex', gap: '8px' }}>
                  <button
                    onClick={() => reject(app.id)}
                    disabled={actionId === app.id}
                    style={{ ...actionBtnStyle, background: '#f44336', flex: 1 }}
                  >
                    {actionId === app.id ? '...' : 'Подтвердить отклонение'}
                  </button>
                  <button
                    onClick={() => { setRejectId(null); setRejectReason('') }}
                    style={{
                      ...actionBtnStyle, flex: 0.6,
                      background: 'transparent',
                      border: '1px solid var(--tg-theme-hint-color, #ccc)',
                      color: 'var(--tg-theme-hint-color, #888)',
                    }}
                  >
                    Отмена
                  </button>
                </div>
              </div>
            ) : (
              <div style={{ display: 'flex', gap: '8px' }}>
                <button
                  onClick={() => approve(app.id)}
                  disabled={actionId !== null}
                  style={{ ...actionBtnStyle, background: '#4CAF50', flex: 1 }}
                >
                  {actionId === app.id ? '...' : 'Принять'}
                </button>
                <button
                  onClick={() => setRejectId(app.id)}
                  disabled={actionId !== null}
                  style={{ ...actionBtnStyle, background: '#f44336', flex: 1 }}
                >
                  Отклонить
                </button>
              </div>
            )}
          </div>
        )
      })}
    </div>
  )
}

// ─── Event Stats View ─────────────────────────────────────────────────────────

function EventStatsView({ event, onBack }: { event: Event; onBack: () => void }) {
  const [stats, setStats] = useState<EventStats | null>(null)
  const [responses, setResponses] = useState<EventResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    Promise.all([
      eventsApi.getEventStats(event.id),
      eventsApi.getEventResponses(event.id),
    ])
      .then(([s, r]) => { setStats(s); setResponses(r) })
      .catch(() => setError('Не удалось загрузить статистику'))
      .finally(() => setLoading(false))
  }, [event.id])

  const surplus = stats ? stats.going - event.participantLimit : 0

  const s1Labels: Record<string, string> = { going: 'Пойду', maybe: 'Возможно', not_going: 'Не пойду' }
  const s1Colors: Record<string, string> = { going: '#4CAF50', maybe: '#FF9800', not_going: '#f44336' }

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '20px' }}>
        <button onClick={onBack} style={backBtnStyle}>←</button>
        <div>
          <h2 style={{ margin: 0, fontSize: '17px' }}>{event.title}</h2>
          <div style={{ fontSize: '12px', color: 'var(--tg-theme-hint-color, #888)' }}>{fmtDatetime(event.eventDatetime)}</div>
        </div>
      </div>

      {loading ? (
        <Skeleton n={4} />
      ) : error ? (
        <div style={{ color: '#f44336', textAlign: 'center', padding: '24px 0', fontSize: '14px' }}>{error}</div>
      ) : stats ? (
        <>
          {surplus > 0 && (
            <div style={{
              padding: '12px 16px', borderRadius: '10px',
              background: 'rgba(76,175,80,0.1)', border: '1px solid rgba(76,175,80,0.3)',
              marginBottom: '14px', fontSize: '14px',
            }}>
              <strong style={{ color: '#4CAF50' }}>Избыток!</strong> Желающих на {surplus} больше лимита.
              Первые {event.participantLimit} по времени ответа получат места.
            </div>
          )}
          {surplus < 0 && (
            <div style={{
              padding: '12px 16px', borderRadius: '10px',
              background: 'rgba(255,152,0,0.1)', border: '1px solid rgba(255,152,0,0.3)',
              marginBottom: '14px', fontSize: '14px',
            }}>
              <strong style={{ color: '#FF9800' }}>Недобор.</strong> Не хватает {Math.abs(surplus)} участников.
              Будет обращение к пулу «Возможно».
            </div>
          )}

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '8px', marginBottom: '16px' }}>
            {[
              { label: 'Пойду', value: stats.going, color: '#4CAF50' },
              { label: 'Возможно', value: stats.maybe, color: '#FF9800' },
              { label: 'Не пойду', value: stats.notGoing, color: '#f44336' },
            ].map(({ label, value, color }) => (
              <div key={label} style={{
                padding: '12px', borderRadius: '10px',
                background: 'var(--tg-theme-secondary-bg-color, #f5f5f5)', textAlign: 'center',
              }}>
                <div style={{ fontSize: '22px', fontWeight: 700, color }}>{value}</div>
                <div style={{ fontSize: '11px', color: 'var(--tg-theme-hint-color, #888)' }}>{label}</div>
              </div>
            ))}
          </div>

          {stats.confirmed > 0 && (
            <div style={{
              padding: '12px 16px', borderRadius: '10px',
              background: 'rgba(33,150,243,0.08)', marginBottom: '16px', fontSize: '14px',
            }}>
              <strong>{stats.confirmed}</strong> / {stats.limit} подтвердило участие
            </div>
          )}

          {responses.length > 0 && (
            <>
              <SectionHeader text="Ответы участников" />
              {responses.map(r => {
                const name = userName(r)
                return (
                  <div key={r.userId} style={{
                    display: 'flex', alignItems: 'center', gap: '10px',
                    padding: '10px 14px', borderRadius: '10px',
                    background: 'var(--tg-theme-secondary-bg-color, #f5f5f5)', marginBottom: '8px',
                  }}>
                    <Avatar name={name} url={r.avatarUrl} size={32} />
                    <div style={{ flex: 1, minWidth: 0, fontWeight: 600, fontSize: '14px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {name}
                    </div>
                    <div style={{ textAlign: 'right', flexShrink: 0 }}>
                      {r.finalStatus ? (
                        <div style={{
                          fontSize: '11px', fontWeight: 600,
                          color: r.finalStatus === 'confirmed' ? '#4CAF50' : r.finalStatus === 'waitlisted' ? '#FF9800' : '#f44336',
                        }}>
                          {r.finalStatus === 'confirmed' ? 'Подтверждено'
                            : r.finalStatus === 'waitlisted' ? `Резерв ${r.positionInWaitlist ?? ''}`
                            : 'Отказ'}
                        </div>
                      ) : r.stage1Status ? (
                        <div style={{ fontSize: '11px', color: s1Colors[r.stage1Status] ?? '#888' }}>
                          {s1Labels[r.stage1Status] ?? r.stage1Status}
                        </div>
                      ) : null}
                    </div>
                  </div>
                )
              })}
            </>
          )}
        </>
      ) : null}
    </div>
  )
}

// ─── Attendance View ──────────────────────────────────────────────────────────

function AttendanceView({ event, onBack }: { event: Event; onBack: () => void }) {
  const [confirmed, setConfirmed] = useState<EventResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [attendance, setAttendance] = useState<Record<string, boolean>>({})
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    eventsApi.getEventResponses(event.id)
      .then(r => {
        const confirmedList = r.filter(resp => resp.finalStatus === 'confirmed')
        setConfirmed(confirmedList)
        const init: Record<string, boolean> = {}
        confirmedList.forEach(resp => { init[resp.userId] = true })
        setAttendance(init)
      })
      .catch(() => setError('Не удалось загрузить список'))
      .finally(() => setLoading(false))
  }, [event.id])

  async function save() {
    setSaving(true)
    try {
      const entries = Object.entries(attendance).map(([userId, attended]) => ({ userId, attended }))
      await eventsApi.markAttendance(event.id, entries)
      setSaved(true)
      setTimeout(onBack, 1500)
    } catch {
      alert('Не удалось сохранить посещаемость')
    } finally {
      setSaving(false)
    }
  }

  function toggle(userId: string) {
    setAttendance(prev => ({ ...prev, [userId]: !prev[userId] }))
  }

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '20px' }}>
        <button onClick={onBack} style={backBtnStyle}>←</button>
        <div>
          <h2 style={{ margin: 0, fontSize: '17px' }}>Посещаемость</h2>
          <div style={{ fontSize: '12px', color: 'var(--tg-theme-hint-color, #888)' }}>{event.title}</div>
        </div>
      </div>

      {loading ? (
        <Skeleton n={4} />
      ) : error ? (
        <div style={{ color: '#f44336', textAlign: 'center', padding: '24px 0', fontSize: '14px' }}>{error}</div>
      ) : confirmed.length === 0 ? (
        <Empty icon="👤" text="Нет подтверждённых участников" />
      ) : (
        <>
          <div style={{ fontSize: '13px', color: 'var(--tg-theme-hint-color, #888)', marginBottom: '16px' }}>
            Отметьте, кто пришёл на встречу. Это влияет на репутацию участников.
          </div>

          {saved && (
            <div style={{
              padding: '12px 16px', borderRadius: '10px',
              background: 'rgba(76,175,80,0.15)', color: '#4CAF50',
              marginBottom: '16px', fontWeight: 600, textAlign: 'center',
            }}>
              Посещаемость сохранена!
            </div>
          )}

          {confirmed.map(r => {
            const name = userName(r)
            const attended = attendance[r.userId] ?? true
            return (
              <div
                key={r.userId}
                onClick={() => toggle(r.userId)}
                style={{
                  display: 'flex', alignItems: 'center', gap: '12px',
                  padding: '14px 16px', borderRadius: '12px',
                  background: 'var(--tg-theme-secondary-bg-color, #f5f5f5)',
                  marginBottom: '8px', cursor: 'pointer',
                  border: `2px solid ${attended ? 'rgba(76,175,80,0.4)' : 'rgba(244,67,54,0.3)'}`,
                  transition: 'border-color 0.2s',
                }}
              >
                <Avatar name={name} url={r.avatarUrl} size={36} />
                <div style={{ flex: 1, fontWeight: 600, fontSize: '15px' }}>{name}</div>
                <div style={{
                  width: '36px', height: '22px', borderRadius: '11px',
                  background: attended ? '#4CAF50' : '#ccc',
                  position: 'relative', flexShrink: 0, transition: 'background 0.2s',
                }}>
                  <div style={{
                    position: 'absolute',
                    top: '3px',
                    left: attended ? '17px' : '3px',
                    width: '16px', height: '16px', borderRadius: '50%', background: '#fff',
                    transition: 'left 0.2s',
                  }} />
                </div>
                <div style={{
                  fontSize: '13px',
                  color: attended ? '#4CAF50' : '#f44336',
                  fontWeight: 600, flexShrink: 0, minWidth: '64px', textAlign: 'right',
                }}>
                  {attended ? 'Пришёл' : 'Не пришёл'}
                </div>
              </div>
            )
          })}

          <div style={{ marginTop: '20px' }}>
            <div style={{ fontSize: '13px', color: 'var(--tg-theme-hint-color, #888)', marginBottom: '12px', textAlign: 'center' }}>
              Пришло: {Object.values(attendance).filter(Boolean).length} из {confirmed.length}
            </div>
            <button
              onClick={save}
              disabled={saving || saved}
              style={{
                width: '100%', padding: '14px', borderRadius: '12px', border: 'none',
                background: saved ? '#4CAF50' : saving ? 'var(--tg-theme-hint-color, #aaa)' : 'var(--tg-theme-button-color, #2196F3)',
                color: '#fff', fontSize: '16px', fontWeight: 600,
                cursor: (saving || saved) ? 'not-allowed' : 'pointer',
              }}
            >
              {saved ? 'Сохранено!' : saving ? 'Сохраняем...' : 'Сохранить посещаемость'}
            </button>
          </div>
        </>
      )}
    </div>
  )
}

// ─── Create Event Form ────────────────────────────────────────────────────────

interface CreateEventFormProps {
  clubId: string
  onSuccess: () => void
  onCancel: () => void
}

function CreateEventForm({ clubId, onSuccess, onCancel }: CreateEventFormProps) {
  const [form, setForm] = useState({
    title: '',
    description: '',
    eventDatetime: '',
    location: '',
    participantLimit: 20,
    votingOpensDaysBefore: 3,
  })
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)

  function validate(): boolean {
    const errs: Record<string, string> = {}
    if (!form.title.trim()) errs.title = 'Введите название'
    if (!form.eventDatetime) errs.eventDatetime = 'Укажите дату и время'
    else if (new Date(form.eventDatetime) <= new Date()) errs.eventDatetime = 'Дата должна быть в будущем'
    if (form.participantLimit < 1) errs.participantLimit = 'Лимит должен быть больше 0'
    if (form.votingOpensDaysBefore < 1 || form.votingOpensDaysBefore > 14) errs.votingOpensDaysBefore = 'От 1 до 14 дней'
    setErrors(errs)
    return Object.keys(errs).length === 0
  }

  async function submit() {
    if (!validate()) return
    setSubmitting(true)
    setSubmitError(null)
    try {
      const payload: CreateEventRequest = {
        title: form.title.trim(),
        ...(form.description.trim() ? { description: form.description.trim() } : {}),
        eventDatetime: new Date(form.eventDatetime).toISOString(),
        ...(form.location.trim() ? { location: form.location.trim() } : {}),
        participantLimit: form.participantLimit,
        votingOpensDaysBefore: form.votingOpensDaysBefore,
      }
      await eventsApi.createEvent(clubId, payload)
      onSuccess()
    } catch (e: unknown) {
      setSubmitError(e instanceof Error ? e.message : 'Ошибка создания события')
    } finally {
      setSubmitting(false)
    }
  }

  function inp(key: keyof typeof form, extra?: React.CSSProperties): React.InputHTMLAttributes<HTMLInputElement> {
    return {
      value: String(form[key]),
      onChange: (e: React.ChangeEvent<HTMLInputElement>) => setForm(p => ({ ...p, [key]: e.target.value })),
      style: { ...inputBaseStyle, borderColor: errors[key] ? '#f44336' : undefined, ...extra },
    }
  }

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '20px' }}>
        <button onClick={onCancel} style={backBtnStyle}>←</button>
        <h2 style={{ margin: 0, fontSize: '18px' }}>Создать событие</h2>
      </div>

      <div style={{ marginBottom: '14px' }}>
        <label style={labelStyle}>Название *</label>
        <input {...inp('title')} placeholder="Воскресная пробежка" />
        {errors.title && <div style={{ fontSize: '12px', color: '#f44336', marginTop: '4px' }}>{errors.title}</div>}
      </div>

      <div style={{ marginBottom: '14px' }}>
        <label style={labelStyle}>Дата и время *</label>
        <input
          type="datetime-local"
          value={form.eventDatetime}
          onChange={e => setForm(p => ({ ...p, eventDatetime: e.target.value }))}
          style={{ ...inputBaseStyle, borderColor: errors.eventDatetime ? '#f44336' : undefined }}
        />
        {errors.eventDatetime && <div style={{ fontSize: '12px', color: '#f44336', marginTop: '4px' }}>{errors.eventDatetime}</div>}
      </div>

      <div style={{ marginBottom: '14px' }}>
        <label style={labelStyle}>Место проведения</label>
        <input {...inp('location')} placeholder="Парк Горького, вход у фонтана" />
      </div>

      <div style={{ marginBottom: '14px' }}>
        <label style={labelStyle}>Лимит участников *</label>
        <input
          type="number"
          value={form.participantLimit}
          min={1}
          onChange={e => setForm(p => ({ ...p, participantLimit: Number(e.target.value) }))}
          style={{ ...inputBaseStyle, borderColor: errors.participantLimit ? '#f44336' : undefined }}
        />
        {errors.participantLimit && <div style={{ fontSize: '12px', color: '#f44336', marginTop: '4px' }}>{errors.participantLimit}</div>}
      </div>

      <div style={{ marginBottom: '14px' }}>
        <label style={labelStyle}>За сколько дней открыть голосование *</label>
        <input
          type="number"
          value={form.votingOpensDaysBefore}
          min={1}
          max={14}
          onChange={e => setForm(p => ({ ...p, votingOpensDaysBefore: Number(e.target.value) }))}
          style={{ ...inputBaseStyle, borderColor: errors.votingOpensDaysBefore ? '#f44336' : undefined }}
        />
        {errors.votingOpensDaysBefore && <div style={{ fontSize: '12px', color: '#f44336', marginTop: '4px' }}>{errors.votingOpensDaysBefore}</div>}
        <div style={{ fontSize: '11px', color: 'var(--tg-theme-hint-color, #aaa)', marginTop: '4px' }}>
          От 1 до 14 дней
        </div>
      </div>

      <div style={{ marginBottom: '20px' }}>
        <label style={labelStyle}>Описание</label>
        <textarea
          value={form.description}
          onChange={e => setForm(p => ({ ...p, description: e.target.value }))}
          placeholder="Подробности о событии..."
          style={{ ...inputBaseStyle, resize: 'none', height: '80px' } as React.CSSProperties}
        />
      </div>

      {submitError && (
        <div style={{
          padding: '12px', borderRadius: '8px',
          background: 'rgba(244,67,54,0.1)', color: '#f44336',
          marginBottom: '12px', fontSize: '14px',
        }}>
          {submitError}
        </div>
      )}

      <button
        onClick={submit}
        disabled={submitting}
        style={{
          width: '100%', padding: '14px', borderRadius: '12px', border: 'none',
          background: submitting ? 'var(--tg-theme-hint-color, #aaa)' : 'var(--tg-theme-button-color, #2196F3)',
          color: '#fff', fontSize: '16px', fontWeight: 600,
          cursor: submitting ? 'not-allowed' : 'pointer',
        }}
      >
        {submitting ? 'Создаём...' : 'Создать событие'}
      </button>
    </div>
  )
}

// ─── Event Card ───────────────────────────────────────────────────────────────

function EventCard({ event, isPast, onClick }: { event: Event; isPast?: boolean; onClick: () => void }) {
  const statusLabels: Record<string, string> = {
    upcoming: 'Ожидает', stage_1: 'Голосование', stage_2: 'Подтверждение',
    completed: 'Завершено', cancelled: 'Отменено',
  }
  const statusColors: Record<string, string> = {
    upcoming: '#888', stage_1: '#2196F3', stage_2: '#FF9800',
    completed: '#4CAF50', cancelled: '#f44336',
  }
  const needsAttendance = isPast && !event.attendanceFinalized && event.status === 'completed'

  return (
    <div
      onClick={onClick}
      style={{
        padding: '14px 16px', borderRadius: '12px', cursor: 'pointer',
        background: 'var(--tg-theme-secondary-bg-color, #f5f5f5)',
        marginBottom: '10px', opacity: isPast && !needsAttendance ? 0.8 : 1,
      }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '6px' }}>
        <div style={{ fontWeight: 600, fontSize: '15px', flex: 1, marginRight: '8px' }}>{event.title}</div>
        <span style={{
          fontSize: '11px', padding: '2px 8px', borderRadius: '10px',
          background: `${statusColors[event.status]}22`,
          color: statusColors[event.status],
          fontWeight: 600, flexShrink: 0,
        }}>
          {statusLabels[event.status] ?? event.status}
        </span>
      </div>
      <div style={{ fontSize: '13px', color: 'var(--tg-theme-hint-color, #888)' }}>
        {fmtDatetime(event.eventDatetime)}
        {event.location ? ` · ${event.location}` : ''}
      </div>
      <div style={{ fontSize: '12px', color: 'var(--tg-theme-hint-color, #888)', marginTop: '4px' }}>
        {event.confirmedCount}/{event.participantLimit} подтверждений
      </div>
      {needsAttendance && (
        <div style={{ marginTop: '8px', fontSize: '12px', color: '#FF9800', fontWeight: 600 }}>
          Нужно отметить посещаемость
        </div>
      )}
    </div>
  )
}

// ─── Events Tab ───────────────────────────────────────────────────────────────

type EventSubView = 'list' | 'create' | 'stats' | 'attendance'

function EventsTab({ club }: { club: Club }) {
  const [subView, setSubView] = useState<EventSubView>('list')
  const [selectedEvent, setSelectedEvent] = useState<Event | null>(null)
  const [upcomingEvents, setUpcomingEvents] = useState<Event[]>([])
  const [pastEvents, setPastEvents] = useState<Event[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const loadEvents = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [upcoming, past] = await Promise.all([
        eventsApi.getClubEvents(club.id, 'upcoming'),
        eventsApi.getClubEvents(club.id, 'past'),
      ])
      setUpcomingEvents(upcoming)
      setPastEvents(past)
    } catch {
      setError('Не удалось загрузить события')
    } finally {
      setLoading(false)
    }
  }, [club.id])

  useEffect(() => {
    if (subView === 'list') loadEvents()
  }, [subView, loadEvents])

  function openEvent(ev: Event, isPast: boolean) {
    setSelectedEvent(ev)
    if (isPast && !ev.attendanceFinalized && ev.status === 'completed') {
      setSubView('attendance')
    } else {
      setSubView('stats')
    }
  }

  function backToList() {
    setSubView('list')
    setSelectedEvent(null)
  }

  if (subView === 'create') {
    return <CreateEventForm clubId={club.id} onSuccess={() => setSubView('list')} onCancel={() => setSubView('list')} />
  }
  if (subView === 'stats' && selectedEvent) {
    return <EventStatsView event={selectedEvent} onBack={backToList} />
  }
  if (subView === 'attendance' && selectedEvent) {
    return <AttendanceView event={selectedEvent} onBack={backToList} />
  }

  return (
    <div>
      <button
        onClick={() => setSubView('create')}
        style={{
          width: '100%', padding: '12px', borderRadius: '10px', border: 'none',
          background: 'var(--tg-theme-button-color, #2196F3)', color: '#fff',
          fontSize: '15px', fontWeight: 600, cursor: 'pointer', marginBottom: '16px',
          display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '6px',
        }}
      >
        + Создать событие
      </button>

      {loading ? (
        <Skeleton n={3} />
      ) : error ? (
        <div style={{ textAlign: 'center', padding: '24px 0' }}>
          <div style={{ color: '#f44336', fontSize: '14px', marginBottom: '12px' }}>{error}</div>
          <button onClick={loadEvents} style={retryBtnStyle}>Повторить</button>
        </div>
      ) : upcomingEvents.length === 0 && pastEvents.length === 0 ? (
        <Empty icon="📅" text="Нет событий" sub="Создайте первое событие для клуба" />
      ) : (
        <>
          {upcomingEvents.length > 0 && (
            <>
              <SectionHeader text="Предстоящие" />
              {upcomingEvents.map(ev => (
                <EventCard key={ev.id} event={ev} onClick={() => openEvent(ev, false)} />
              ))}
            </>
          )}
          {pastEvents.length > 0 && (
            <>
              <SectionHeader text="Прошедшие" />
              {pastEvents.map(ev => (
                <EventCard key={ev.id} event={ev} isPast onClick={() => openEvent(ev, true)} />
              ))}
            </>
          )}
        </>
      )}
    </div>
  )
}

// ─── Finances Tab ─────────────────────────────────────────────────────────────

function FinancesTab({ clubId }: { clubId: string }) {
  const [stats, setStats] = useState<FinancialStats | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    clubsApi.getFinancialStats(clubId)
      .then(setStats)
      .catch(() => setError('Не удалось загрузить финансовую статистику'))
      .finally(() => setLoading(false))
  }, [clubId])

  if (loading) return <Skeleton n={4} />
  if (error) return (
    <div style={{ color: '#f44336', textAlign: 'center', padding: '32px 0', fontSize: '14px' }}>{error}</div>
  )
  if (!stats) return <Empty icon="💰" text="Нет данных" />

  function StatCard({ label, value, color }: { label: string; value: string; color?: string }) {
    return (
      <div style={{
        padding: '16px', borderRadius: '12px',
        background: 'var(--tg-theme-secondary-bg-color, #f5f5f5)', marginBottom: '10px',
      }}>
        <div style={{ fontSize: '13px', color: 'var(--tg-theme-hint-color, #888)', marginBottom: '4px' }}>{label}</div>
        <div style={{ fontSize: '22px', fontWeight: 700, color: color ?? 'var(--tg-theme-text-color, #000)' }}>{value}</div>
      </div>
    )
  }

  return (
    <div>
      <StatCard label="Активных участников" value={String(stats.activeMembers)} />
      <StatCard label="Оборот за месяц" value={`${stats.monthlyRevenueStars.toLocaleString()} Stars`} />
      <StatCard label="Ваш доход (80%)" value={`${stats.organizerShare.toLocaleString()} Stars`} color="#4CAF50" />
      <StatCard label="Комиссия платформы (20%)" value={`${stats.platformShare.toLocaleString()} Stars`} />
      {stats.nextBillingDate && (
        <StatCard label="Ближайшее списание" value={fmtDate(stats.nextBillingDate)} />
      )}
    </div>
  )
}

// ─── Main Component ───────────────────────────────────────────────────────────

type ManageTab = 'members' | 'applications' | 'events' | 'finances'

const TAB_LABELS: Record<ManageTab, string> = {
  members: 'Участники',
  applications: 'Заявки',
  events: 'События',
  finances: 'Финансы',
}

export function OrganizerClubManage({ club, onBack }: { club: Club; onBack: () => void }) {
  const [tab, setTab] = useState<ManageTab>('members')

  return (
    <div style={{ paddingBottom: '100px' }}>
      {/* Header */}
      <div style={{
        padding: '16px', display: 'flex', alignItems: 'center', gap: '12px',
        borderBottom: '1px solid var(--tg-theme-secondary-bg-color, #eee)',
      }}>
        <button onClick={onBack} style={backBtnStyle}>←</button>
        <div>
          <h2 style={{ margin: 0, fontSize: '17px' }}>{club.name}</h2>
          <div style={{ fontSize: '12px', color: 'var(--tg-theme-hint-color, #888)' }}>
            {club.confirmedCount}/{club.memberLimit} участников · Управление
          </div>
        </div>
      </div>

      {/* Tab bar */}
      <div style={{
        display: 'flex', borderBottom: '1px solid var(--tg-theme-secondary-bg-color, #eee)',
        background: 'var(--tg-theme-bg-color, #fff)', position: 'sticky', top: 0, zIndex: 10,
      }}>
        {(Object.keys(TAB_LABELS) as ManageTab[]).map(t => (
          <button
            key={t}
            onClick={() => setTab(t)}
            style={{
              flex: 1, padding: '12px 4px', border: 'none', background: 'none', cursor: 'pointer',
              fontSize: '12px', fontWeight: tab === t ? 700 : 400,
              color: tab === t ? 'var(--tg-theme-button-color, #2196F3)' : 'var(--tg-theme-hint-color, #888)',
              borderBottom: tab === t ? '2px solid var(--tg-theme-button-color, #2196F3)' : '2px solid transparent',
              transition: 'color 0.15s, border-color 0.15s',
            }}
          >
            {TAB_LABELS[t]}
          </button>
        ))}
      </div>

      {/* Tab content */}
      <div style={{ padding: '16px' }}>
        {tab === 'members' && <MembersTab clubId={club.id} />}
        {tab === 'applications' && <ApplicationsTab clubId={club.id} />}
        {tab === 'events' && <EventsTab club={club} />}
        {tab === 'finances' && <FinancesTab clubId={club.id} />}
      </div>
    </div>
  )
}
