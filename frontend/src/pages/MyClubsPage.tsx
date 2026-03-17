import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { clubsApi } from '../api/clubs'
import { membershipApi, type ApplicationDto } from '../api/membership'
import type { Club } from '../types/club'
import { CATEGORY_LABELS } from '../types/club'

function formatPrice(price: number): string {
  if (price === 0) return 'Бесплатно'
  return `${price} ⭐/мес`
}

const APPLICATION_STATUS_LABELS: Record<string, { label: string; color: string }> = {
  pending: { label: 'На рассмотрении', color: '#FF9800' },
  approved: { label: 'Одобрена', color: '#4CAF50' },
  rejected: { label: 'Отклонена', color: '#f44336' },
  auto_rejected: { label: 'Автоотклонена', color: '#f44336' },
}

export function MyClubsPage() {
  const navigate = useNavigate()
  const [clubs, setClubs] = useState<Club[]>([])
  const [applications, setApplications] = useState<ApplicationDto[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [tab, setTab] = useState<'clubs' | 'applications'>('clubs')

  const load = () => {
    setIsLoading(true)
    setError(null)
    Promise.allSettled([
      clubsApi.getMyClubs(),
      membershipApi.getMyApplications(),
    ]).then(([clubsRes, appsRes]) => {
      if (clubsRes.status === 'fulfilled') setClubs(clubsRes.value)
      if (appsRes.status === 'fulfilled') setApplications(appsRes.value)
      if (clubsRes.status === 'rejected' && appsRes.status === 'rejected') {
        setError('Не удалось загрузить данные')
      }
      setIsLoading(false)
    })
  }

  useEffect(() => { load() }, [])

  const pendingApps = applications.filter((a) => a.status === 'pending')

  return (
    <div style={{ padding: '0 12px', paddingBottom: 100 }}>
      {/* Header */}
      <div style={{ padding: '16px 0 12px' }}>
        <h1 style={{ margin: 0, fontSize: 22, fontWeight: 700, color: 'var(--tg-theme-text-color, #000)' }}>
          Мои клубы
        </h1>
      </div>

      {/* Tabs */}
      <div style={{ display: 'flex', gap: 0, marginBottom: 16, borderRadius: 12, overflow: 'hidden', border: '1px solid var(--tg-theme-hint-color, #ddd)' }}>
        <button
          onClick={() => setTab('clubs')}
          style={{
            flex: 1,
            padding: '10px 0',
            border: 'none',
            fontSize: 14,
            fontWeight: 600,
            cursor: 'pointer',
            background: tab === 'clubs' ? 'var(--tg-theme-button-color, #2196F3)' : 'var(--tg-theme-secondary-bg-color, #f5f5f5)',
            color: tab === 'clubs' ? 'var(--tg-theme-button-text-color, #fff)' : 'var(--tg-theme-text-color, #000)',
          }}
        >
          Клубы ({clubs.length})
        </button>
        <button
          onClick={() => setTab('applications')}
          style={{
            flex: 1,
            padding: '10px 0',
            border: 'none',
            fontSize: 14,
            fontWeight: 600,
            cursor: 'pointer',
            background: tab === 'applications' ? 'var(--tg-theme-button-color, #2196F3)' : 'var(--tg-theme-secondary-bg-color, #f5f5f5)',
            color: tab === 'applications' ? 'var(--tg-theme-button-text-color, #fff)' : 'var(--tg-theme-text-color, #000)',
            position: 'relative',
          }}
        >
          Заявки
          {pendingApps.length > 0 && (
            <span style={{
              position: 'absolute',
              top: 4,
              right: 12,
              background: '#FF9800',
              color: '#fff',
              borderRadius: 10,
              padding: '1px 6px',
              fontSize: 11,
              fontWeight: 700,
              minWidth: 16,
              textAlign: 'center',
            }}>
              {pendingApps.length}
            </span>
          )}
        </button>
      </div>

      {/* Error */}
      {error && (
        <div style={{ textAlign: 'center', padding: 24 }}>
          <p style={{ color: 'var(--tg-theme-destructive-text-color, #f44336)', marginBottom: 12 }}>{error}</p>
          <button
            onClick={load}
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
            Повторить
          </button>
        </div>
      )}

      {/* Loading */}
      {isLoading && (
        <>
          <ClubRowSkeleton />
          <ClubRowSkeleton />
          <ClubRowSkeleton />
        </>
      )}

      {/* Clubs tab */}
      {!isLoading && !error && tab === 'clubs' && (
        <>
          {clubs.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '48px 24px' }}>
              <p style={{ fontSize: 40, margin: '0 0 12px' }}>🏠</p>
              <p style={{ fontSize: 16, fontWeight: 600, color: 'var(--tg-theme-text-color, #000)', marginBottom: 8 }}>
                Вы пока не состоите в клубах
              </p>
              <p style={{ fontSize: 14, color: 'var(--tg-theme-hint-color, #888)', marginBottom: 20 }}>
                Найдите интересный клуб на вкладке Discovery
              </p>
              <button
                onClick={() => navigate('/')}
                style={{
                  padding: '10px 24px',
                  borderRadius: 12,
                  border: 'none',
                  background: 'var(--tg-theme-button-color, #2196F3)',
                  color: 'var(--tg-theme-button-text-color, #fff)',
                  cursor: 'pointer',
                  fontSize: 14,
                  fontWeight: 600,
                }}
              >
                Найти клуб
              </button>
            </div>
          ) : (
            clubs.map((club) => (
              <ClubRow key={club.id} club={club} onClick={() => navigate(`/clubs/${club.id}/interior`)} />
            ))
          )}
        </>
      )}

      {/* Applications tab */}
      {!isLoading && !error && tab === 'applications' && (
        <>
          {applications.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '48px 24px' }}>
              <p style={{ fontSize: 40, margin: '0 0 12px' }}>📋</p>
              <p style={{ fontSize: 16, fontWeight: 600, color: 'var(--tg-theme-text-color, #000)', marginBottom: 8 }}>
                Заявок пока нет
              </p>
              <p style={{ fontSize: 14, color: 'var(--tg-theme-hint-color, #888)' }}>
                Подайте заявку в закрытый клуб, и она появится здесь
              </p>
            </div>
          ) : (
            applications.map((app) => (
              <ApplicationRow key={app.id} app={app} onClick={() => navigate(`/clubs/${app.clubId}`)} />
            ))
          )}
        </>
      )}
    </div>
  )
}

function ClubRow({ club, onClick }: { club: Club; onClick: () => void }) {
  return (
    <div
      onClick={onClick}
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 12,
        padding: 12,
        background: 'var(--tg-theme-bg-color, #fff)',
        borderRadius: 14,
        marginBottom: 8,
        cursor: 'pointer',
        boxShadow: '0 1px 3px rgba(0,0,0,0.06)',
      }}
    >
      {/* Avatar */}
      <div
        style={{
          width: 52,
          height: 52,
          borderRadius: 14,
          flexShrink: 0,
          background: club.avatarUrl
            ? `url(${club.avatarUrl}) center/cover`
            : 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: '#fff',
          fontSize: 22,
          fontWeight: 700,
        }}
      >
        {!club.avatarUrl && club.name.charAt(0).toUpperCase()}
      </div>

      {/* Info */}
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{
          fontSize: 15,
          fontWeight: 600,
          color: 'var(--tg-theme-text-color, #000)',
          marginBottom: 3,
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
        }}>
          {club.name}
        </div>
        <div style={{ display: 'flex', gap: 8, fontSize: 12, color: 'var(--tg-theme-hint-color, #888)' }}>
          <span>{CATEGORY_LABELS[club.category] ?? club.category}</span>
          {club.city && <span>• {club.city}</span>}
        </div>
      </div>

      {/* Price / Arrow */}
      <div style={{ textAlign: 'right', flexShrink: 0 }}>
        <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--tg-theme-text-color, #000)', marginBottom: 2 }}>
          {formatPrice(club.subscriptionPrice)}
        </div>
        <div style={{ fontSize: 11, color: 'var(--tg-theme-hint-color, #aaa)' }}>
          {club.confirmedCount}/{club.memberLimit}
        </div>
      </div>

      <div style={{ color: 'var(--tg-theme-hint-color, #ccc)', fontSize: 18 }}>›</div>
    </div>
  )
}

function ApplicationRow({ app, onClick }: { app: ApplicationDto; onClick: () => void }) {
  const statusInfo = APPLICATION_STATUS_LABELS[app.status] ?? { label: app.status, color: '#888' }
  const date = new Date(app.createdAt)
  const dateStr = date.toLocaleDateString('ru-RU', { day: 'numeric', month: 'short' })

  return (
    <div
      onClick={onClick}
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 12,
        padding: 12,
        background: 'var(--tg-theme-bg-color, #fff)',
        borderRadius: 14,
        marginBottom: 8,
        cursor: 'pointer',
        boxShadow: '0 1px 3px rgba(0,0,0,0.06)',
      }}
    >
      <div style={{
        width: 42,
        height: 42,
        borderRadius: 12,
        flexShrink: 0,
        background: 'var(--tg-theme-secondary-bg-color, #f0f0f0)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        fontSize: 20,
      }}>
        {app.status === 'pending' ? '⏳' : app.status === 'approved' ? '✅' : '❌'}
      </div>

      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{
          fontSize: 14,
          fontWeight: 600,
          color: 'var(--tg-theme-text-color, #000)',
          marginBottom: 3,
        }}>
          Заявка от {dateStr}
        </div>
        <div style={{ fontSize: 12, color: statusInfo.color, fontWeight: 500 }}>
          {statusInfo.label}
        </div>
      </div>

      <div style={{ color: 'var(--tg-theme-hint-color, #ccc)', fontSize: 18 }}>›</div>
    </div>
  )
}

function ClubRowSkeleton() {
  return (
    <div style={{
      display: 'flex',
      alignItems: 'center',
      gap: 12,
      padding: 12,
      background: 'var(--tg-theme-bg-color, #fff)',
      borderRadius: 14,
      marginBottom: 8,
    }}>
      <div style={{ width: 52, height: 52, borderRadius: 14, background: 'var(--tg-theme-secondary-bg-color, #f0f0f0)' }} />
      <div style={{ flex: 1 }}>
        <div style={{ height: 14, width: '60%', background: 'var(--tg-theme-secondary-bg-color, #f0f0f0)', borderRadius: 6, marginBottom: 6 }} />
        <div style={{ height: 10, width: '40%', background: 'var(--tg-theme-secondary-bg-color, #f0f0f0)', borderRadius: 6 }} />
      </div>
    </div>
  )
}
