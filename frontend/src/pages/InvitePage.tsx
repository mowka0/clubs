import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { clubsApi } from '../api/clubs'
import { membershipApi } from '../api/membership'
import { paymentsApi, openTelegramInvoice } from '../api/payments'
import type { Club } from '../types/club'
import { CATEGORY_LABELS } from '../types/club'
import { ApiError } from '../api/apiClient'

type InviteStep = 'loading' | 'club' | 'confirm' | 'processing' | 'success' | 'error'

export function InvitePage() {
  const { code } = useParams<{ code: string }>()
  const navigate = useNavigate()
  useAuth()

  const [club, setClub] = useState<Club | null>(null)
  const [step, setStep] = useState<InviteStep>('loading')
  const [loadError, setLoadError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [agreed, setAgreed] = useState(false)

  useEffect(() => {
    if (!code) {
      setLoadError('Неверная ссылка приглашения')
      setStep('error')
      return
    }
    setStep('loading')
    clubsApi.getClubByInvite(code)
      .then((c) => {
        setClub(c)
        setStep('club')
      })
      .catch((err) => {
        if (err instanceof ApiError && err.status === 404) {
          setLoadError('Ссылка приглашения недействительна или уже использована')
        } else {
          setLoadError('Не удалось загрузить данные клуба')
        }
        setStep('error')
      })
  }, [code])

  const handleJoinByInvite = async () => {
    if (!code || !club) return
    setActionError(null)

    if (club.subscriptionPrice > 0) {
      setStep('processing')
      try {
        const { invoiceLink } = await paymentsApi.createInvoice(club.id)
        openTelegramInvoice(invoiceLink, async (status) => {
          if (status === 'paid') {
            try {
              await membershipApi.joinByInvite(code)
              setStep('success')
            } catch {
              // Membership might already be created by webhook
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
      setStep('processing')
      try {
        await membershipApi.joinByInvite(code)
        setStep('success')
      } catch (err) {
        const msg = err instanceof ApiError ? err.message : 'Не удалось вступить в клуб'
        // If already a member, treat as success
        if (err instanceof ApiError && (err.status === 409 || err.message.toLowerCase().includes('already'))) {
          setStep('success')
        } else {
          setActionError(msg)
          setStep('confirm')
        }
      }
    }
  }

  // ─── Loading ────────────────────────────────────────────────────────────────
  if (step === 'loading') {
    return (
      <div style={{ padding: 16 }}>
        {[200, 24, 16, 14].map((h, i) => (
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
  if (step === 'error' || !club) {
    return (
      <div style={{ textAlign: 'center', padding: '48px 24px' }}>
        <p style={{ fontSize: 40, margin: '0 0 12px' }}>🔗</p>
        <h2 style={{ margin: '0 0 8px', fontSize: 18, color: 'var(--tg-theme-text-color, #000)' }}>
          Ссылка недействительна
        </h2>
        <p style={{ margin: '0 0 24px', fontSize: 14, color: 'var(--tg-theme-hint-color, #888)' }}>
          {loadError ?? 'Ссылка приглашения не найдена'}
        </p>
        <button onClick={() => navigate('/')} style={btnSecondaryStyle}>
          На главную
        </button>
      </div>
    )
  }

  // ─── Success ─────────────────────────────────────────────────────────────────
  if (step === 'success') {
    return (
      <div style={{ padding: 24, textAlign: 'center' }}>
        <p style={{ fontSize: 48, margin: '32px 0 16px' }}>🎉</p>
        <h2 style={{ margin: '0 0 8px', fontSize: 20, color: 'var(--tg-theme-text-color, #000)' }}>
          Добро пожаловать!
        </h2>
        <p style={{ margin: '0 0 24px', fontSize: 14, color: 'var(--tg-theme-hint-color, #888)' }}>
          Вы вступили в клуб «{club.name}»
        </p>
        <button onClick={() => navigate(`/clubs/${club.id}/interior`)} style={btnPrimaryStyle}>
          Открыть клуб
        </button>
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

        <div style={cardStyle}>
          <p style={{ margin: '0 0 4px', fontSize: 16, fontWeight: 600, color: 'var(--tg-theme-text-color, #000)' }}>
            {club.name}
          </p>
          <p style={{ margin: 0, fontSize: 13, color: 'var(--tg-theme-hint-color, #888)' }}>
            {CATEGORY_LABELS[club.category] ?? club.category}
            {club.city && ` • ${club.city}`}
          </p>
        </div>

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

        <label style={{ display: 'flex', alignItems: 'flex-start', gap: 12, padding: '12px 0', cursor: 'pointer' }}>
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

        {actionError && (
          <p style={{ color: 'var(--tg-theme-destructive-text-color, #f44336)', fontSize: 14, marginBottom: 12 }}>
            {actionError}
          </p>
        )}

        <div style={fixedBottomStyle}>
          <div style={{ display: 'flex', gap: 8 }}>
            <button onClick={() => setStep('club')} style={{ ...btnSecondaryStyle, flex: 1 }}>
              Назад
            </button>
            <button
              onClick={handleJoinByInvite}
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

  // ─── Club card view ──────────────────────────────────────────────────────────
  const isFull = club.confirmedCount >= club.memberLimit

  return (
    <div style={{ paddingBottom: 100 }}>
      {/* Cover */}
      <div
        style={{
          height: 180,
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
              bottom: -28,
              left: 20,
              width: 56,
              height: 56,
              borderRadius: 14,
              border: '3px solid var(--tg-theme-bg-color, #fff)',
              overflow: 'hidden',
            }}
          >
            <img src={club.avatarUrl} alt={club.name} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
          </div>
        )}
      </div>

      <div style={{ padding: club.avatarUrl ? '40px 16px 16px' : '16px' }}>
        {/* Invite badge */}
        <div
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: 6,
            background: 'var(--tg-theme-button-color, #2196F3)',
            color: 'var(--tg-theme-button-text-color, #fff)',
            borderRadius: 8,
            padding: '4px 10px',
            fontSize: 12,
            fontWeight: 600,
            marginBottom: 10,
          }}
        >
          🔒 Приватный клуб · По приглашению
        </div>

        <h1 style={{ margin: '0 0 6px', fontSize: 22, fontWeight: 700, color: 'var(--tg-theme-text-color, #000)' }}>
          {club.name}
        </h1>

        <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', marginBottom: 14, fontSize: 13, color: 'var(--tg-theme-hint-color, #888)' }}>
          <span>{CATEGORY_LABELS[club.category] ?? club.category}</span>
          {club.city && <span>• {club.city}</span>}
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

      <div style={fixedBottomStyle}>
        {isFull ? (
          <button disabled style={{ ...btnPrimaryStyle, background: 'var(--tg-theme-secondary-bg-color, #f0f0f0)', color: 'var(--tg-theme-hint-color, #888)', cursor: 'default' }}>
            Клуб заполнен
          </button>
        ) : (
          <button
            onClick={() => { setAgreed(false); setActionError(null); setStep('confirm') }}
            style={btnPrimaryStyle}
          >
            {club.subscriptionPrice > 0 ? `Вступить за ${club.subscriptionPrice} ⭐/мес` : 'Вступить по приглашению'}
          </button>
        )}
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
