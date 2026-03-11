import { useNavigate, useLocation } from 'react-router-dom'
import { Tabbar } from '@telegram-apps/telegram-ui'

interface Tab {
  path: string
  label: string
  icon: string
}

const TABS: Tab[] = [
  { path: '/', label: 'Discovery', icon: '🔍' },
  { path: '/my-clubs', label: 'Мои клубы', icon: '🏠' },
  { path: '/profile', label: 'Профиль', icon: '👤' },
]

export function BottomTabBar() {
  const navigate = useNavigate()
  const location = useLocation()

  const activeTab = TABS.find((t) =>
    t.path === '/' ? location.pathname === '/' : location.pathname.startsWith(t.path)
  ) ?? TABS[0]

  return (
    <Tabbar>
      {TABS.map((tab) => (
        <button
          key={tab.path}
          onClick={() => navigate(tab.path)}
          style={{
            flex: 1,
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            padding: '8px 0',
            background: 'none',
            border: 'none',
            cursor: 'pointer',
            color: tab === activeTab ? 'var(--tg-theme-button-color, #2196F3)' : 'var(--tg-theme-hint-color, #888)',
            fontSize: '10px',
          }}
        >
          <span style={{ fontSize: '22px', marginBottom: '2px' }}>{tab.icon}</span>
          <span style={{ fontWeight: tab === activeTab ? 700 : 400 }}>{tab.label}</span>
        </button>
      ))}
    </Tabbar>
  )
}
