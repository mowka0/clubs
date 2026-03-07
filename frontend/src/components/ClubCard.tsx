import { useNavigate } from 'react-router-dom'
import type { Club } from '../types/club'
import { CATEGORY_LABELS } from '../types/club'

interface Props {
  club: Club
}

function formatPrice(price: number): string {
  if (price === 0) return 'Бесплатно'
  return `${price} ⭐/мес`
}

function getAccessIcon(accessType: string): string {
  if (accessType === 'open') return '🔓'
  if (accessType === 'closed') return '🔒'
  return '🔐'
}

export function ClubCard({ club }: Props) {
  const navigate = useNavigate()

  return (
    <div
      onClick={() => navigate(`/clubs/${club.id}`)}
      style={{
        background: 'var(--tg-theme-bg-color, #fff)',
        borderRadius: 16,
        overflow: 'hidden',
        marginBottom: 12,
        cursor: 'pointer',
        boxShadow: '0 1px 4px rgba(0,0,0,0.08)',
      }}
    >
      {/* Cover / Avatar area */}
      <div
        style={{
          height: 120,
          background: club.coverUrl
            ? `url(${club.coverUrl}) center/cover`
            : 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
          position: 'relative',
          display: 'flex',
          alignItems: 'flex-end',
          padding: '0 12px 8px',
        }}
      >
        {club.avatarUrl && (
          <img
            src={club.avatarUrl}
            alt={club.name}
            style={{
              width: 44,
              height: 44,
              borderRadius: 12,
              border: '2px solid rgba(255,255,255,0.8)',
              objectFit: 'cover',
              marginRight: 8,
            }}
          />
        )}
        {/* Promo tags */}
        <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap', flex: 1 }}>
          {club.promoTags.map((tag) => (
            <span
              key={tag}
              style={{
                background: 'rgba(255,255,255,0.9)',
                borderRadius: 6,
                padding: '2px 6px',
                fontSize: 11,
                fontWeight: 600,
                color: '#333',
              }}
            >
              {tag}
            </span>
          ))}
        </div>
      </div>

      {/* Card body */}
      <div style={{ padding: '10px 12px 12px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 4 }}>
          <h3
            style={{
              margin: 0,
              fontSize: 15,
              fontWeight: 600,
              color: 'var(--tg-theme-text-color, #000)',
              flex: 1,
              marginRight: 8,
            }}
          >
            {club.name}
          </h3>
          <span style={{ fontSize: 13, color: 'var(--tg-theme-hint-color, #999)', whiteSpace: 'nowrap' }}>
            {getAccessIcon(club.accessType)} {formatPrice(club.subscriptionPrice)}
          </span>
        </div>

        {/* Category + city */}
        <div style={{ display: 'flex', gap: 8, marginBottom: 6, fontSize: 12, color: 'var(--tg-theme-hint-color, #888)' }}>
          <span>{CATEGORY_LABELS[club.category] ?? club.category}</span>
          {club.city && <span>• {club.city}</span>}
        </div>

        {club.description && (
          <p
            style={{
              margin: '0 0 8px',
              fontSize: 13,
              color: 'var(--tg-theme-hint-color, #666)',
              display: '-webkit-box',
              WebkitLineClamp: 2,
              WebkitBoxOrient: 'vertical',
              overflow: 'hidden',
            }}
          >
            {club.description}
          </p>
        )}

        {/* Footer: members + going indicator */}
        <div style={{ display: 'flex', gap: 12, fontSize: 12, color: 'var(--tg-theme-hint-color, #888)' }}>
          <span>
            {club.confirmedCount}/{club.memberLimit} участников
          </span>
          {club.goingCount > 0 && (
            <span style={{ color: '#4CAF50' }}>
              {club.goingCount} идут на встречу
            </span>
          )}
        </div>
      </div>
    </div>
  )
}

export function ClubCardSkeleton() {
  return (
    <div
      style={{
        background: 'var(--tg-theme-bg-color, #fff)',
        borderRadius: 16,
        overflow: 'hidden',
        marginBottom: 12,
      }}
    >
      <div style={{ height: 120, background: 'var(--tg-theme-secondary-bg-color, #f0f0f0)' }} />
      <div style={{ padding: '10px 12px 12px' }}>
        <div style={{ height: 16, width: '60%', background: 'var(--tg-theme-secondary-bg-color, #f0f0f0)', borderRadius: 6, marginBottom: 8 }} />
        <div style={{ height: 12, width: '40%', background: 'var(--tg-theme-secondary-bg-color, #f0f0f0)', borderRadius: 6, marginBottom: 8 }} />
        <div style={{ height: 12, width: '80%', background: 'var(--tg-theme-secondary-bg-color, #f0f0f0)', borderRadius: 6 }} />
      </div>
    </div>
  )
}
