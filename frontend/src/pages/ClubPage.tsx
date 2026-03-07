import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { clubsApi } from '../api/clubs'
import { membershipApi } from '../api/membership'
import { useAuth } from '../hooks/useAuth'
import type { Club } from '../types/club'
import { CATEGORY_LABELS, ACCESS_TYPE_LABELS } from '../types/club'
import type { MembershipDto } from '../api/membership'
import { ApiError } from '../api/apiClient'

export function ClubPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { user } = useAuth()

  const [club, setClub] = useState<Club | null>(null)
  const [membership, setMembership] = useState<MembershipDto | null | 'none'>('none')
  const [loading, setLoading] = useState(true)
  const [joinLoading, setJoinLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [joinError, setJoinError] = useState<string | null>(null)
  const [joinSuccess, setJoinSuccess] = useState(false)
  const [showApplyForm, setShowApplyForm] = useState(false)
  const [applyAnswer, setApplyAnswer] = useState('')
  const [applySuccess, setApplySuccess] = useState(false)

  useEffect(() => {
    if (!id) return
    setLoading(true)
    setError(null)

    clubsApi.getClub(id)
      .then(setClub)
      .catch(() => setError('Клуб не найден'))
      .finally(() => setLoading(false))
  }, [id])

  // Check if current user is already a member
  useEffect(() => {
    if (!id || !user) return
    clubsApi.getMembers(id)
      .then((members) => {
        const found = members.find((m) => m.userId === user.id)
        if (found) {
          setMembership({
            id: '',
            userId: user.id,
            clubId: id,
            role: found.role as MembershipDto['role'],
            status: 'active',
            joinedAt: found.joinedAt,
            subscriptionExpiresAt: null,
            lockedSubscriptionPrice: null,
          })
        } else {
          setMembership(null)
        }
      })
      .catch(() => {
        // 403 means not a member yet (expected for non-members)
        setMembership(null)
      })
  }, [id, user])

  const handleJoin = async () => {
    if (!id) return
    setJoinLoading(true)
    setJoinError(null)
    try {
      await membershipApi.joinClub(id)
      setJoinSuccess(true)
      // Navigate to club interior after joining
      setTimeout(() => navigate(`/clubs/${id}/interior`), 1500)
    } catch (err) {
      if (err instanceof ApiError) {
        setJoinError(err.message)
      } else {
        setJoinError('Не удалось вступить в клуб')
      }
    } finally {
      setJoinLoading(false)
    }
  }

  const handleApply = async () => {
    if (!id) return
    setJoinLoading(true)
    setJoinError(null)
    try {
      await membershipApi.applyToClub(id, applyAnswer)
      setApplySuccess(true)
      setShowApplyForm(false)
    } catch (err) {
      if (err instanceof ApiError) {
        setJoinError(err.message)
      } else {
        setJoinError('Не удалось подать заявку')
      }
    } finally {
      setJoinLoading(false)
    }
  }

  if (loading) {
    return (
      <div style={{ padding: 16 }}>
        <div style={{ height: 200, background: 'var(--tg-theme-secondary-bg-color, #f0f0f0)', borderRadius: 16, marginBottom: 16 }} />
        <div style={{ height: 24, width: '60%', background: 'var(--tg-theme-secondary-bg-color, #f0f0f0)', borderRadius: 8, marginBottom: 12 }} />
        <div style={{ height: 16, width: '40%', background: 'var(--tg-theme-secondary-bg-color, #f0f0f0)', borderRadius: 8, marginBottom: 8 }} />
        <div style={{ height: 14, background: 'var(--tg-theme-secondary-bg-color, #f0f0f0)', borderRadius: 8, marginBottom: 8 }} />
        <div style={{ height: 14, width: '80%', background: 'var(--tg-theme-secondary-bg-color, #f0f0f0)', borderRadius: 8 }} />
      </div>
    )
  }

  if (error || !club) {
    return (
      <div style={{ textAlign: 'center', padding: '48px 24px' }}>
        <p style={{ fontSize: 32, margin: '0 0 12px' }}>😕</p>
        <p style={{ fontSize: 16, fontWeight: 600, color: 'var(--tg-theme-text-color, #000)', marginBottom: 8 }}>
          {error ?? 'Клуб не найден'}
        </p>
        <button
          onClick={() => navigate(-1)}
          style={{
            padding: '10px 24px',
            borderRadius: 12,
            border: 'none',
            background: 'var(--tg-theme-button-color, #2196F3)',
            color: 'var(--tg-theme-button-text-color, #fff)',
            cursor: 'pointer',
            fontSize: 14,
          }}
        >
          Назад
        </button>
      </div>
    )
  }

  const isMember = membership !== null && membership !== 'none'
  const isOwner = user?.id === club.ownerId
  const isFull = club.confirmedCount >= club.memberLimit

  return (
    <div style={{ paddingBottom: 100 }}>
      {/* Cover */}
      <div
        style={{
          height: 220,
          background: club.coverUrl
            ? `url(${club.coverUrl}) center/cover`
            : 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
          position: 'relative',
        }}
      >
        {club.avatarUrl && (
          <div
            style={{
              position: 'absolute',
              bottom: -30,
              left: 20,
              width: 64,
              height: 64,
              borderRadius: 16,
              border: '3px solid var(--tg-theme-bg-color, #fff)',
              overflow: 'hidden',
            }}
          >
            <img src={club.avatarUrl} alt={club.name} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
          </div>
        )}
      </div>

      {/* Content */}
      <div style={{ padding: club.avatarUrl ? '44px 16px 16px' : '16px' }}>
        {/* Promo tags */}
        {club.promoTags.length > 0 && (
          <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginBottom: 8 }}>
            {club.promoTags.map((tag) => (
              <span
                key={tag}
                style={{
                  background: 'var(--tg-theme-button-color, #2196F3)',
                  color: 'var(--tg-theme-button-text-color, #fff)',
                  borderRadius: 8,
                  padding: '3px 10px',
                  fontSize: 12,
                  fontWeight: 600,
                }}
              >
                {tag}
              </span>
            ))}
          </div>
        )}

        {/* Title */}
        <h1 style={{ margin: '0 0 6px', fontSize: 22, fontWeight: 700, color: 'var(--tg-theme-text-color, #000)' }}>
          {club.name}
        </h1>

        {/* Meta */}
        <div style={{ display: 'flex', gap: 12, marginBottom: 12, fontSize: 13, color: 'var(--tg-theme-hint-color, #888)', flexWrap: 'wrap' }}>
          <span>{CATEGORY_LABELS[club.category] ?? club.category}</span>
          {club.city && <span>• {club.city}</span>}
          <span>• {ACCESS_TYPE_LABELS[club.accessType] ?? club.accessType}</span>
          <span>• {club.confirmedCount}/{club.memberLimit} участников</span>
          {club.subscriptionPrice > 0 && <span>• {club.subscriptionPrice} ⭐/мес</span>}
          {club.subscriptionPrice === 0 && <span>• Бесплатно</span>}
        </div>

        {/* Description */}
        {club.description && (
          <p style={{ margin: '0 0 16px', fontSize: 15, color: 'var(--tg-theme-text-color, #222)', lineHeight: 1.5 }}>
            {club.description}
          </p>
        )}

        {/* Rules */}
        {club.rules && (
          <div
            style={{
              background: 'var(--tg-theme-secondary-bg-color, #f5f5f5)',
              borderRadius: 12,
              padding: 12,
              marginBottom: 16,
            }}
          >
            <p style={{ margin: '0 0 4px', fontSize: 13, fontWeight: 600, color: 'var(--tg-theme-text-color, #000)' }}>
              Правила клуба
            </p>
            <p style={{ margin: 0, fontSize: 13, color: 'var(--tg-theme-hint-color, #666)', lineHeight: 1.5 }}>
              {club.rules}
            </p>
          </div>
        )}

        {/* Success states */}
        {joinSuccess && (
          <div
            style={{
              background: '#E8F5E9',
              borderRadius: 12,
              padding: 16,
              marginBottom: 16,
              textAlign: 'center',
              color: '#2E7D32',
              fontSize: 15,
              fontWeight: 600,
            }}
          >
            Вы успешно вступили в клуб!
          </div>
        )}
        {applySuccess && (
          <div
            style={{
              background: '#E3F2FD',
              borderRadius: 12,
              padding: 16,
              marginBottom: 16,
              textAlign: 'center',
              color: '#1565C0',
              fontSize: 15,
            }}
          >
            <p style={{ margin: '0 0 4px', fontWeight: 600 }}>Заявка отправлена!</p>
            <p style={{ margin: 0, fontSize: 13 }}>Обычно ответ приходит в течение нескольких часов.</p>
          </div>
        )}

        {/* Error */}
        {joinError && (
          <p style={{ color: 'var(--tg-theme-destructive-text-color, #f44336)', fontSize: 14, marginBottom: 12 }}>
            {joinError}
          </p>
        )}

        {/* Apply form */}
        {showApplyForm && (
          <div style={{ marginBottom: 16 }}>
            {club.applicationQuestion && (
              <p style={{ margin: '0 0 8px', fontSize: 14, fontWeight: 600, color: 'var(--tg-theme-text-color, #000)' }}>
                {club.applicationQuestion}
              </p>
            )}
            <textarea
              value={applyAnswer}
              onChange={(e) => setApplyAnswer(e.target.value)}
              placeholder="Ваш ответ..."
              rows={4}
              style={{
                width: '100%',
                padding: 12,
                borderRadius: 12,
                border: '1px solid var(--tg-theme-hint-color, #ddd)',
                background: 'var(--tg-theme-secondary-bg-color, #f5f5f5)',
                color: 'var(--tg-theme-text-color, #000)',
                fontSize: 14,
                outline: 'none',
                resize: 'vertical',
                boxSizing: 'border-box',
              }}
            />
            <div style={{ display: 'flex', gap: 8, marginTop: 8 }}>
              <button
                onClick={() => setShowApplyForm(false)}
                style={{
                  flex: 1,
                  padding: 12,
                  borderRadius: 12,
                  border: '1px solid var(--tg-theme-hint-color, #ddd)',
                  background: 'transparent',
                  color: 'var(--tg-theme-text-color, #000)',
                  fontSize: 14,
                  cursor: 'pointer',
                }}
              >
                Отмена
              </button>
              <button
                onClick={handleApply}
                disabled={joinLoading}
                style={{
                  flex: 2,
                  padding: 12,
                  borderRadius: 12,
                  border: 'none',
                  background: 'var(--tg-theme-button-color, #2196F3)',
                  color: 'var(--tg-theme-button-text-color, #fff)',
                  fontSize: 14,
                  cursor: 'pointer',
                  fontWeight: 600,
                  opacity: joinLoading ? 0.7 : 1,
                }}
              >
                {joinLoading ? 'Отправка...' : 'Отправить заявку'}
              </button>
            </div>
          </div>
        )}

        {/* Action buttons */}
        <div style={{ position: 'fixed', bottom: 60, left: 0, right: 0, padding: '12px 16px', background: 'var(--tg-theme-bg-color, #fff)', borderTop: '1px solid var(--tg-theme-secondary-bg-color, #f0f0f0)' }}>
          {isMember || isOwner ? (
            <button
              onClick={() => navigate(`/clubs/${id}/interior`)}
              style={{
                width: '100%',
                padding: 14,
                borderRadius: 14,
                border: 'none',
                background: 'var(--tg-theme-button-color, #2196F3)',
                color: 'var(--tg-theme-button-text-color, #fff)',
                fontSize: 15,
                fontWeight: 600,
                cursor: 'pointer',
              }}
            >
              Открыть клуб
            </button>
          ) : applySuccess ? null : joinSuccess ? null : isFull ? (
            <button
              disabled
              style={{
                width: '100%',
                padding: 14,
                borderRadius: 14,
                border: 'none',
                background: 'var(--tg-theme-secondary-bg-color, #f0f0f0)',
                color: 'var(--tg-theme-hint-color, #888)',
                fontSize: 15,
                fontWeight: 600,
                cursor: 'default',
              }}
            >
              Клуб заполнен
            </button>
          ) : club.accessType === 'open' ? (
            <button
              onClick={handleJoin}
              disabled={joinLoading}
              style={{
                width: '100%',
                padding: 14,
                borderRadius: 14,
                border: 'none',
                background: 'var(--tg-theme-button-color, #2196F3)',
                color: 'var(--tg-theme-button-text-color, #fff)',
                fontSize: 15,
                fontWeight: 600,
                cursor: 'pointer',
                opacity: joinLoading ? 0.7 : 1,
              }}
            >
              {joinLoading ? 'Вступаем...' : club.subscriptionPrice > 0 ? `Вступить за ${club.subscriptionPrice} ⭐/мес` : 'Вступить'}
            </button>
          ) : club.accessType === 'closed' && !showApplyForm ? (
            <button
              onClick={() => setShowApplyForm(true)}
              style={{
                width: '100%',
                padding: 14,
                borderRadius: 14,
                border: 'none',
                background: 'var(--tg-theme-button-color, #2196F3)',
                color: 'var(--tg-theme-button-text-color, #fff)',
                fontSize: 15,
                fontWeight: 600,
                cursor: 'pointer',
              }}
            >
              Хочу вступить
            </button>
          ) : null}
        </div>
      </div>
    </div>
  )
}
