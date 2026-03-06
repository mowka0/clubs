import { useNavigate, useLocation } from 'react-router-dom'
import { Tabbar, TabbarItem } from '@telegram-apps/telegram-ui'

interface Tab {
  path: string
  label: string
}

const TABS: Tab[] = [
  { path: '/', label: 'Discovery' },
  { path: '/my-clubs', label: 'Мои клубы' },
  { path: '/profile', label: 'Профиль' },
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
        <TabbarItem
          key={tab.path}
          selected={tab === activeTab}
          onClick={() => navigate(tab.path)}
          text={tab.label}
        />
      ))}
    </Tabbar>
  )
}
