import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { clubsApi } from '../api/clubs'
import { membershipApi } from '../api/membership'
import { paymentsApi, openTelegramInvoice } from '../api/payments'
import { useAuth } from '../hooks/useAuth'
import type { Club } from '../types/club'
import { CATEGORY_LABELS, ACCESS_TYPE_LABELS } from '../types/club'
import { ApiError } from '../api/apiClient'

type JoinStep = 'view' | 'confirm' | 'apply' | 'processing' | 'success' | 'apply-success'

export function ClubPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { user } = useAuth()

  const [club, setClub] = useState<Club | null>(null)
  const [isMember, setIsMember] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [step, setStep] = useState<JoinStep>('view')
  const [agreed, setAgreed] = useState(false)
  const [applyAnswer, setApplyAnswer] = useState('')
  const [actionError, setActionError] = useState<string | null>(null)

  useEffect(() => {
    if (!id) return
    setLoading(true)
    setError(null)
    clubsApi.getClub(id)
      .then(setClub)
      .catch(() => setError('Клуб не найден'))
      .finally(() => setLoading(false))
  }, [id])

  useEffect(() => {
    if (!id || !user) return
    clubsApi.getMembers(id)
      .then((members) => {
        setIsMember(members.some((m) => m.userId === user.id))
      })
      .catch(() => {
        // 403 for non-members — not a member yet
        setIsMember(false)
      })
  }, [id, user])

  const handleJoinDirect = async () => {
    if (!id) return
    setStep('processing')
    setActionError(null)
    try {
      await membershipApi.joinClub(id)
      setStep('success')
    } catch (err) {
      const msg = err instanceof ApiError ? err.message : 'Не удалось вступить в клуб'
      setActionError(msg)
      setStep('confirm')
    }
  }

  const handleConfirmJoin = async () => {
    if (!id || !club) return
    setActionError(null)

    if (club.subscriptionPrice > 0) {
      // Paid club — trigger Telegram Stars payment
      setStep('processing')
      try {
        const { invoiceLink } = await paymentsApi.createInvoice(id)
        openTelegramInvoice(invoiceLink, async (status) => {
          if (status === 'paid') {
            // Payment done — backend handles membership creation via webhook
            // Then join/confirm membership via API
            try {
              await membershipApi.joinClub(id)
              setStep('success')
            } catch {
              // Membership might already be created by payment webhook
              setStep('success')
            }
          } else if (status === 'cancelled') {
            setStep('confirm')
          } else {
            setActionError('Оплата не прошла. Попробуйте ещё раз.')
            setStep('confirm')
          }
        })
      } catch {
        setActionError('Не удалось создать счёт для оплаты')
        setStep('confirm')
      }
    } else {
      // Free club — join directly
      await handleJoinDirect()
    }
  }

  const handleApplySubmit = async () => {
    if (!id) return
    setStep('processing')
    setActionError(null)
    try {
      await membershipApi.applyToClub(id, applyAnswer)
      setStep('apply-success')
    } catch (err) {
      const msg = err instanceof ApiError ? err.message : 'Не удалось подать заявку'
      setActionError(msg)
      setStep('apply')
    }
  }

  // ─── Loading ────────────────────────────────────────────────────────────────
  if (loading) {
    return (
      <div style={{ padding: 16 }}>
        {[200, 24, 16, 14, 14].map((h, i) => (
          <div
            key={i}
            style={{
              height: h,
              width: i === 1 ? '60%' : i === 2 ? '40%' : '100%',
              background: 'var(--tg-theme-secondary-bg-color, #f0f0f0)',
              borderRadius: i === 0 ? 16 : 8,
              marginBottom: i === 0 ? 16 : 8,
            }}
          />
        ))}
      </div>
    )
  }

  // ─── Error ──────────────────────────────────────────────────────────────────
  if (error || !club) {
    return (
      <div style={{ textAlign: 'center', padding: '48px 24px' }}>
        <p style={{ fontSize: 32, margin: '0 0 12px' }}>😕</p>
        <p style={{ fontSize: 16, fontWeight: 600, color: 'var(--tg-theme-text-color, #000)', marginBottom: 8 }}>
          {error ?? 'Клуб не найден'}
        </p>
        <button onClick={() => navigate(-1)} style={btnSecondaryStyle}>Назад</button>
      </div>
    )
  }

  const isOwner = user?.id === club.ownerId
  const isFull = club.confirmedCount >= club.memberLimit

  // ─── Success state ───────────────────────────────────────────────────────────
  if (step === 'success') {
    return (
      <div style={{ padding: 24, textAlign: 'center' }}>
        <p style={{ fontSize: 48, margin: '32px 0 16px' }}>🎉</p>
        <h2 style={{ margin: '0 0 8px', fontSize: 20, color: 'var(--tg-theme-text-color, #000)' }}>
          Вы вступили в клуб!
        </h2>
        <p style={{ margin: '0 0 24px', fontSize: 14, color: 'var(--tg-theme-hint-color, #888)' }}>
          {club.name}
        </p>
        <button
          onClick={() => navigate(`/clubs/${id}/interior`)}
          style={btnPrimaryStyle}
        >
          Открыть клуб
        </button>
      </div>
    )
  }

  // ─── Apply success ───────────────────────────────────────────────────────────
  if (step === 'apply-success') {
    return (
      <div style={{ padding: 24, textAlign: 'center' }}>
        <p style={{ fontSize: 48, margin: '32px 0 16px' }}>📬</p>
        <h2 style={{ margin: '0 0 8px', fontSize: 20, color: 'var(--tg-theme-text-color, #000)' }}>
          Заявка отправлена!
        </h2>
        <p style={{ margin: '0 0 24px', fontSize: 14, color: 'var(--tg-theme-hint-color, #888)' }}>
          Обычно ответ приходит в течение нескольких часов.
        </p>
        <button onClick={() => navigate('/')} style={btnSecondaryStyle}>На главную</button>
      </div>
    )
  }

  // ─── Processing ──────────────────────────────────────────────────────────────
  if (step === 'processing') {
    return (
      <div style={{ padding: 24, textAlign: 'center' }}>
        <p style={{ fontSize: 32, margin: '32px 0 16px' }}>⏳</p>
        <p style={{ fontSize: 16, color: 'var(--tg-theme-text-color, #000)' }}>
          {club.subscriptionPrice > 0 ? 'Обработка оплаты...' : 'Вступаем...'}
        </p>
      </div>
    )
  }

  // ─── Confirmation step ───────────────────────────────────────────────────────
  if (step === 'confirm') {
    return (
      <div style={{ padding: '16px 16px 100px' }}>
        <h2 style={{ margin: '0 0 16px', fontSize: 20, fontWeight: 700, color: 'var(--tg-theme-text-color, #000)' }}>
          Вступление в клуб
        </h2>

        {/* Club name */}
        <div style={cardStyle}>
          <p style={{ margin: '0 0 4px', fontSize: 16, fontWeight: 600, color: 'var(--tg-theme-text-color, #000)' }}>
            {club.name}
          </p>
          <p style={{ margin: 0, fontSize: 13, color: 'var(--tg-theme-hint-color, #888)' }}>
            {CATEGORY_LABELS[club.category] ?? club.category}
            {club.city && ` • ${club.city}`}
          </p>
        </div>

        {/* Price */}
        {club.subscriptionPrice > 0 && (
          <div style={{ ...cardStyle, background: 'var(--tg-theme-button-color, #2196F3)', color: '#fff' }}>
            <p style={{ margin: '0 0 4px', fontSize: 13, opacity: 0.85 }}>Стоимость подписки</p>
            <p style={{ margin: 0, fontSize: 22, fontWeight: 700 }}>
              {club.subscriptionPrice} ⭐ / мес
            </p>
          </div>
        )}
        {club.subscriptionPrice === 0 && (
          <div style={{ ...cardStyle, background: '#E8F5E9' }}>
            <p style={{ margin: 0, fontSize: 15, fontWeight: 600, color: '#2E7D32' }}>Бесплатный клуб</p>
          </div>
        )}

        {/* Description */}
        {club.description && (
          <div style={cardStyle}>
            <p style={{ margin: '0 0 6px', fontSize: 13, fontWeight: 600, color: 'var(--tg-theme-text-color, #000)' }}>
              О клубе
            </p>
            <p style={{ margin: 0, fontSize: 14, color: 'var(--tg-theme-text-color, #444)', lineHeight: 1.5 }}>
              {club.description}
            </p>
          </div>
        )}

        {/* Rules */}
        {club.rules && (
          <div style={cardStyle}>
            <p style={{ margin: '0 0 6px', fontSize: 13, fontWeight: 600, color: 'var(--tg-theme-text-color, #000)' }}>
              Правила клуба
            </p>
            <p style={{ margin: 0, fontSize: 14, color: 'var(--tg-theme-hint-color, #666)', lineHeight: 1.5 }}>
              {club.rules}
            </p>
          </div>
        )}

        {/* Agreement checkbox */}
        <label
          style={{
            display: 'flex',
            alignItems: 'flex-start',
            gap: 12,
            padding: '12px 0',
            cursor: 'pointer',
          }}
        >
          <input
            type="checkbox"
            checked={agreed}
            onChange={(e) => setAgreed(e.target.checked)}
            style={{ width: 20, height: 20, marginTop: 2, flexShrink: 0, cursor: 'pointer' }}
          />
          <span style={{ fontSize: 14, color: 'var(--tg-theme-text-color, #000)', lineHeight: 1.5 }}>
            Я ознакомился с правилами и условиями клуба и согласен с ними
          </span>
        </label>

        {/* Error */}
        {actionError && (
          <p style={{ color: 'var(--tg-theme-destructive-text-color, #f44336)', fontSize: 14, marginBottom: 12 }}>
            {actionError}
          </p>
        )}

        {/* Actions */}
        <div style={fixedBottomStyle}>
          <div style={{ display: 'flex', gap: 8 }}>
            <button onClick={() => setStep('view')} style={{ ...btnSecondaryStyle, flex: 1 }}>
              Назад
            </button>
            <button
              onClick={handleConfirmJoin}
              disabled={!agreed}
              style={{ ...btnPrimaryStyle, flex: 2, opacity: agreed ? 1 : 0.5 }}
            >
              {club.subscriptionPrice > 0 ? `Оплатить ${club.subscriptionPrice} ⭐` : 'Вступить'}
            </button>
          </div>
        </div>
      </div>
    )
  }

  // ─── Apply form ──────────────────────────────────────────────────────────────
  if (step === 'apply') {
    return (
      <div style={{ padding: '16px 16px 100px' }}>
        <h2 style={{ margin: '0 0 8px', fontSize: 20, fontWeight: 700, color: 'var(--tg-theme-text-color, #000)' }}>
          Заявка в клуб
        </h2>
        <p style={{ margin: '0 0 16px', fontSize: 14, color: 'var(--tg-theme-hint-color, #888)' }}>
          {club.name}
        </p>

        {club.applicationQuestion && (
          <div style={cardStyle}>
            <p style={{ margin: '0 0 8px', fontSize: 14, fontWeight: 600, color: 'var(--tg-theme-text-color, #000)' }}>
              {club.applicationQuestion}
            </p>
            <textarea
              value={applyAnswer}
              onChange={(e) => setApplyAnswer(e.target.value)}
              placeholder="Ваш ответ..."
              rows={5}
              style={{
                width: '100%',
                padding: 12,
                borderRadius: 12,
                border: '1px solid var(--tg-theme-hint-color, #ddd)',
                background: 'var(--tg-theme-bg-color, #fff)',
                color: 'var(--tg-theme-text-color, #000)',
                fontSize: 14,
                outline: 'none',
                resize: 'vertical',
                boxSizing: 'border-box',
              }}
            />
          </div>
        )}

        {!club.applicationQuestion && (
          <p style={{ fontSize: 14, color: 'var(--tg-theme-text-color, #444)', marginBottom: 16 }}>
            Нажмите «Отправить заявку», чтобы организатор мог рассмотреть вашу кандидатуру.
          </p>
        )}

        {actionError && (
          <p style={{ color: 'var(--tg-theme-destructive-text-color, #f44336)', fontSize: 14, marginBottom: 12 }}>
            {actionError}
          </p>
        )}

        <div style={fixedBottomStyle}>
          <div style={{ display: 'flex', gap: 8 }}>
            <button onClick={() => setStep('view')} style={{ ...btnSecondaryStyle, flex: 1 }}>
              Назад
            </button>
            <button onClick={handleApplySubmit} style={{ ...btnPrimaryStyle, flex: 2 }}>
              Отправить заявку
            </button>
          </div>
        </div>
      </div>
    )
  }

  // ─── Main club view ──────────────────────────────────────────────────────────
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

        <h1 style={{ margin: '0 0 6px', fontSize: 22, fontWeight: 700, color: 'var(--tg-theme-text-color, #000)' }}>
          {club.name}
        </h1>

        <div style={{ display: 'flex', gap: 12, marginBottom: 12, fontSize: 13, color: 'var(--tg-theme-hint-color, #888)', flexWrap: 'wrap' }}>
          <span>{CATEGORY_LABELS[club.category] ?? club.category}</span>
          {club.city && <span>• {club.city}</span>}
          <span>• {ACCESS_TYPE_LABELS[club.accessType] ?? club.accessType}</span>
          <span>• {club.confirmedCount}/{club.memberLimit} участников</span>
          {club.subscriptionPrice > 0 && <span>• {club.subscriptionPrice} ⭐/мес</span>}
          {club.subscriptionPrice === 0 && <span>• Бесплатно</span>}
        </div>

        {club.description && (
          <p style={{ margin: '0 0 16px', fontSize: 15, color: 'var(--tg-theme-text-color, #222)', lineHeight: 1.5 }}>
            {club.description}
          </p>
        )}

        {club.rules && (
          <div style={{ ...cardStyle, marginBottom: 16 }}>
            <p style={{ margin: '0 0 4px', fontSize: 13, fontWeight: 600, color: 'var(--tg-theme-text-color, #000)' }}>
              Правила клуба
            </p>
            <p style={{ margin: 0, fontSize: 13, color: 'var(--tg-theme-hint-color, #666)', lineHeight: 1.5 }}>
              {club.rules}
            </p>
          </div>
        )}
      </div>

      {/* Fixed bottom action */}
      <div style={fixedBottomStyle}>
        {isMember || isOwner ? (
          <button onClick={() => navigate(`/clubs/${id}/interior`)} style={btnPrimaryStyle}>
            Открыть клуб
          </button>
        ) : isFull ? (
          <button disabled style={{ ...btnPrimaryStyle, background: 'var(--tg-theme-secondary-bg-color, #f0f0f0)', color: 'var(--tg-theme-hint-color, #888)', cursor: 'default' }}>
            Клуб заполнен
          </button>
        ) : club.accessType === 'open' ? (
          <button onClick={() => { setAgreed(false); setActionError(null); setStep('confirm') }} style={btnPrimaryStyle}>
            {club.subscriptionPrice > 0 ? `Вступить за ${club.subscriptionPrice} ⭐/мес` : 'Вступить'}
          </button>
        ) : club.accessType === 'closed' ? (
          <button onClick={() => { setApplyAnswer(''); setActionError(null); setStep('apply') }} style={btnPrimaryStyle}>
            Хочу вступить
          </button>
        ) : null}
      </div>
    </div>
  )
}

// ─── Shared styles ─────────────────────────────────────────────────────────────

const btnPrimaryStyle: React.CSSProperties = {
  width: '100%',
  padding: 14,
  borderRadius: 14,
  border: 'none',
  background: 'var(--tg-theme-button-color, #2196F3)',
  color: 'var(--tg-theme-button-text-color, #fff)',
  fontSize: 15,
  fontWeight: 600,
  cursor: 'pointer',
}

const btnSecondaryStyle: React.CSSProperties = {
  width: '100%',
  padding: 14,
  borderRadius: 14,
  border: '1px solid var(--tg-theme-hint-color, #ddd)',
  background: 'transparent',
  color: 'var(--tg-theme-text-color, #000)',
  fontSize: 15,
  fontWeight: 600,
  cursor: 'pointer',
}

const cardStyle: React.CSSProperties = {
  background: 'var(--tg-theme-secondary-bg-color, #f5f5f5)',
  borderRadius: 12,
  padding: 12,
  marginBottom: 12,
}

const fixedBottomStyle: React.CSSProperties = {
  position: 'fixed',
  bottom: 60,
  left: 0,
  right: 0,
  padding: '12px 16px',
  background: 'var(--tg-theme-bg-color, #fff)',
  borderTop: '1px solid var(--tg-theme-secondary-bg-color, #f0f0f0)',
}
