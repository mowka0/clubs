import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import { clubsApi } from '../api/clubs'
import { membershipApi, type ApplicationDto } from '../api/membership'
import type { Club } from '../types/club'
import { CATEGORY_LABELS } from '../types/club'

function getInitials(firstName: string | null, _lastName: string | null, username: string | null): string {
  if (firstName) return firstName.charAt(0).toUpperCase()
  if (username) return username.charAt(0).toUpperCase()
  return '?'
}

function getDisplayName(firstName: string | null, lastName: string | null, username: string | null): string {
  const parts: string[] = []
  if (firstName) parts.push(firstName)
  if (lastName) parts.push(lastName)
  if (parts.length > 0) return parts.join(' ')
  if (username) return `@${username}`
  return 'Пользователь'
}

export function ProfilePage() {
  const { user, isLoading: authLoading, isAuthenticated, logout } = useAuth()
  const navigate = useNavigate()
  const [clubs, setClubs] = useState<Club[]>([])
  const [applications, setApplications] = useState<ApplicationDto[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [showLogoutConfirm, setShowLogoutConfirm] = useState(false)

  useEffect(() => {
    if (!isAuthenticated && !authLoading) return
    setIsLoading(true)
    Promise.allSettled([
      clubsApi.getMyClubs(),
      membershipApi.getMyApplications(),
    ]).then(([clubsRes, appsRes]) => {
      if (clubsRes.status === 'fulfilled') setClubs(clubsRes.value)
      if (appsRes.status === 'fulfilled') setApplications(appsRes.value)
      setIsLoading(false)
    })
  }, [isAuthenticated, authLoading])

  if (authLoading) {
    return (
      <div style={{ padding: '0 12px', paddingBottom: 100 }}>
        <div style={{ padding: '16px 0 12px' }}>
          <h1 style={{ margin: 0, fontSize: 22, fontWeight: 700, color: 'var(--tg-theme-text-color, #000)' }}>Профиль</h1>
        </div>
        <ProfileSkeleton />
      </div>
    )
  }

  const pendingApps = applications.filter((a) => a.status === 'pending')
  const totalSpend = clubs.reduce((sum, c) => sum + c.subscriptionPrice, 0)

  return (
    <div style={{ padding: '0 12px', paddingBottom: 100 }}>
      {/* Header */}
      <div style={{ padding: '16px 0 12px' }}>
        <h1 style={{ margin: 0, fontSize: 22, fontWeight: 700, color: 'var(--tg-theme-text-color, #000)' }}>
          Профиль
        </h1>
      </div>

      {/* User card */}
      <div style={{
        background: 'var(--tg-theme-bg-color, #fff)',
        borderRadius: 16,
        padding: 20,
        marginBottom: 16,
        boxShadow: '0 1px 4px rgba(0,0,0,0.08)',
        display: 'flex',
        alignItems: 'center',
        gap: 16,
      }}>
        {/* Avatar */}
        {user?.avatarUrl ? (
          <img
            src={user.avatarUrl}
            alt="avatar"
            style={{ width: 64, height: 64, borderRadius: 20, objectFit: 'cover', flexShrink: 0 }}
          />
        ) : (
          <div style={{
            width: 64,
            height: 64,
            borderRadius: 20,
            flexShrink: 0,
            background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: '#fff',
            fontSize: 26,
            fontWeight: 700,
          }}>
            {getInitials(user?.firstName ?? null, user?.lastName ?? null, user?.username ?? null)}
          </div>
        )}

        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{
            fontSize: 18,
            fontWeight: 700,
            color: 'var(--tg-theme-text-color, #000)',
            marginBottom: 2,
          }}>
            {getDisplayName(user?.firstName ?? null, user?.lastName ?? null, user?.username ?? null)}
          </div>
          {user?.username && (
            <div style={{ fontSize: 14, color: 'var(--tg-theme-hint-color, #888)' }}>
              @{user.username}
            </div>
          )}
        </div>
      </div>

      {/* Stats grid */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: '1fr 1fr 1fr',
        gap: 8,
        marginBottom: 16,
      }}>
        <StatCard label="Клубов" value={isLoading ? '...' : String(clubs.length)} emoji="🏠" />
        <StatCard label="Заявки" value={isLoading ? '...' : String(pendingApps.length)} emoji="⏳" />
        <StatCard label="⭐/мес" value={isLoading ? '...' : String(totalSpend)} emoji="" />
      </div>

      {/* My Clubs section */}
      <SectionHeader title="Мои клубы" count={clubs.length} />
      {isLoading ? (
        <>
          <MiniClubSkeleton />
          <MiniClubSkeleton />
        </>
      ) : clubs.length === 0 ? (
        <EmptyBlock
          emoji="🔍"
          text="Вы пока не состоите в клубах"
          actionText="Найти клуб"
          onAction={() => navigate('/')}
        />
      ) : (
        clubs.slice(0, 5).map((club) => (
          <MiniClubRow key={club.id} club={club} onClick={() => navigate(`/clubs/${club.id}/interior`)} />
        ))
      )}
      {clubs.length > 5 && (
        <button
          onClick={() => navigate('/my-clubs')}
          style={{
            width: '100%',
            padding: 10,
            background: 'none',
            border: '1px solid var(--tg-theme-hint-color, #ddd)',
            borderRadius: 10,
            fontSize: 13,
            color: 'var(--tg-theme-button-color, #2196F3)',
            cursor: 'pointer',
            marginBottom: 16,
            fontWeight: 600,
          }}
        >
          Показать все ({clubs.length})
        </button>
      )}

      {/* Pending Applications */}
      {pendingApps.length > 0 && (
        <>
          <SectionHeader title="Активные заявки" count={pendingApps.length} />
          {pendingApps.map((app) => (
            <div
              key={app.id}
              onClick={() => navigate(`/clubs/${app.clubId}`)}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 10,
                padding: 10,
                background: 'var(--tg-theme-bg-color, #fff)',
                borderRadius: 12,
                marginBottom: 6,
                cursor: 'pointer',
                boxShadow: '0 1px 2px rgba(0,0,0,0.04)',
              }}
            >
              <span style={{ fontSize: 20 }}>⏳</span>
              <div style={{ flex: 1, fontSize: 13, color: 'var(--tg-theme-text-color, #000)' }}>
                Заявка от {new Date(app.createdAt).toLocaleDateString('ru-RU', { day: 'numeric', month: 'short' })}
              </div>
              <span style={{ fontSize: 12, color: '#FF9800', fontWeight: 500 }}>На рассмотрении</span>
            </div>
          ))}
        </>
      )}

      {/* Logout */}
      <div style={{ marginTop: 24 }}>
        {!showLogoutConfirm ? (
          <button
            onClick={() => setShowLogoutConfirm(true)}
            style={{
              width: '100%',
              padding: '12px 0',
              borderRadius: 12,
              border: '1px solid var(--tg-theme-destructive-text-color, #f44336)',
              background: 'none',
              color: 'var(--tg-theme-destructive-text-color, #f44336)',
              fontSize: 14,
              fontWeight: 600,
              cursor: 'pointer',
            }}
          >
            Выйти из аккаунта
          </button>
        ) : (
          <div style={{
            background: 'var(--tg-theme-secondary-bg-color, #f5f5f5)',
            borderRadius: 12,
            padding: 16,
            textAlign: 'center',
          }}>
            <p style={{ margin: '0 0 12px', fontSize: 14, color: 'var(--tg-theme-text-color, #000)' }}>
              Вы уверены?
            </p>
            <div style={{ display: 'flex', gap: 8 }}>
              <button
                onClick={() => setShowLogoutConfirm(false)}
                style={{
                  flex: 1,
                  padding: '10px 0',
                  borderRadius: 10,
                  border: '1px solid var(--tg-theme-hint-color, #ddd)',
                  background: 'none',
                  color: 'var(--tg-theme-text-color, #000)',
                  fontSize: 14,
                  cursor: 'pointer',
                }}
              >
                Отмена
              </button>
              <button
                onClick={logout}
                style={{
                  flex: 1,
                  padding: '10px 0',
                  borderRadius: 10,
                  border: 'none',
                  background: 'var(--tg-theme-destructive-text-color, #f44336)',
                  color: '#fff',
                  fontSize: 14,
                  fontWeight: 600,
                  cursor: 'pointer',
                }}
              >
                Выйти
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}

function StatCard({ label, value, emoji }: { label: string; value: string; emoji: string }) {
  return (
    <div style={{
      background: 'var(--tg-theme-bg-color, #fff)',
      borderRadius: 14,
      padding: '14px 8px',
      textAlign: 'center',
      boxShadow: '0 1px 3px rgba(0,0,0,0.06)',
    }}>
      {emoji && <div style={{ fontSize: 20, marginBottom: 4 }}>{emoji}</div>}
      <div style={{ fontSize: 20, fontWeight: 700, color: 'var(--tg-theme-text-color, #000)' }}>{value}</div>
      <div style={{ fontSize: 11, color: 'var(--tg-theme-hint-color, #888)', marginTop: 2 }}>{label}</div>
    </div>
  )
}

function SectionHeader({ title, count }: { title: string; count: number }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8, marginTop: 8 }}>
      <h2 style={{ margin: 0, fontSize: 16, fontWeight: 700, color: 'var(--tg-theme-text-color, #000)' }}>{title}</h2>
      <span style={{ fontSize: 13, color: 'var(--tg-theme-hint-color, #888)' }}>{count}</span>
    </div>
  )
}

function MiniClubRow({ club, onClick }: { club: Club; onClick: () => void }) {
  return (
    <div
      onClick={onClick}
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 10,
        padding: 10,
        background: 'var(--tg-theme-bg-color, #fff)',
        borderRadius: 12,
        marginBottom: 6,
        cursor: 'pointer',
        boxShadow: '0 1px 2px rgba(0,0,0,0.04)',
      }}
    >
      <div style={{
        width: 40,
        height: 40,
        borderRadius: 12,
        flexShrink: 0,
        background: club.avatarUrl
          ? `url(${club.avatarUrl}) center/cover`
          : 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        color: '#fff',
        fontSize: 16,
        fontWeight: 700,
      }}>
        {!club.avatarUrl && club.name.charAt(0).toUpperCase()}
      </div>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{
          fontSize: 14,
          fontWeight: 600,
          color: 'var(--tg-theme-text-color, #000)',
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
        }}>
          {club.name}
        </div>
        <div style={{ fontSize: 11, color: 'var(--tg-theme-hint-color, #888)' }}>
          {CATEGORY_LABELS[club.category] ?? club.category}
          {club.city ? ` • ${club.city}` : ''}
        </div>
      </div>
      <div style={{ color: 'var(--tg-theme-hint-color, #ccc)', fontSize: 16 }}>›</div>
    </div>
  )
}

function EmptyBlock({ emoji, text, actionText, onAction }: { emoji: string; text: string; actionText: string; onAction: () => void }) {
  return (
    <div style={{ textAlign: 'center', padding: '32px 16px' }}>
      <p style={{ fontSize: 32, margin: '0 0 8px' }}>{emoji}</p>
      <p style={{ fontSize: 14, color: 'var(--tg-theme-hint-color, #888)', marginBottom: 14 }}>{text}</p>
      <button
        onClick={onAction}
        style={{
          padding: '8px 20px',
          borderRadius: 10,
          border: 'none',
          background: 'var(--tg-theme-button-color, #2196F3)',
          color: 'var(--tg-theme-button-text-color, #fff)',
          cursor: 'pointer',
          fontSize: 13,
          fontWeight: 600,
        }}
      >
        {actionText}
      </button>
    </div>
  )
}

function MiniClubSkeleton() {
  return (
    <div style={{
      display: 'flex',
      alignItems: 'center',
      gap: 10,
      padding: 10,
      background: 'var(--tg-theme-bg-color, #fff)',
      borderRadius: 12,
      marginBottom: 6,
    }}>
      <div style={{ width: 40, height: 40, borderRadius: 12, background: 'var(--tg-theme-secondary-bg-color, #f0f0f0)' }} />
      <div style={{ flex: 1 }}>
        <div style={{ height: 12, width: '50%', background: 'var(--tg-theme-secondary-bg-color, #f0f0f0)', borderRadius: 6, marginBottom: 5 }} />
        <div style={{ height: 10, width: '35%', background: 'var(--tg-theme-secondary-bg-color, #f0f0f0)', borderRadius: 6 }} />
      </div>
    </div>
  )
}

function ProfileSkeleton() {
  return (
    <>
      <div style={{
        background: 'var(--tg-theme-bg-color, #fff)',
        borderRadius: 16,
        padding: 20,
        marginBottom: 16,
        display: 'flex',
        alignItems: 'center',
        gap: 16,
      }}>
        <div style={{ width: 64, height: 64, borderRadius: 20, background: 'var(--tg-theme-secondary-bg-color, #f0f0f0)' }} />
        <div style={{ flex: 1 }}>
          <div style={{ height: 18, width: '50%', background: 'var(--tg-theme-secondary-bg-color, #f0f0f0)', borderRadius: 6, marginBottom: 8 }} />
          <div style={{ height: 14, width: '30%', background: 'var(--tg-theme-secondary-bg-color, #f0f0f0)', borderRadius: 6 }} />
        </div>
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 8, marginBottom: 16 }}>
        {[1, 2, 3].map((i) => (
          <div key={i} style={{ height: 80, background: 'var(--tg-theme-secondary-bg-color, #f0f0f0)', borderRadius: 14 }} />
        ))}
      </div>
    </>
  )
}
