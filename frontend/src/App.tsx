import { AppRoot } from '@telegram-apps/telegram-ui'
import { BrowserRouter } from 'react-router-dom'
import { AppRouter } from './router'
import { BottomTabBar } from './components/BottomTabBar'
import { BackButtonHandler } from './telegram/BackButtonHandler'
import { DeepLinkHandler } from './telegram/DeepLinkHandler'

export function App() {
  return (
    <AppRoot>
      <BrowserRouter>
        <BackButtonHandler />
        <DeepLinkHandler />
        <div style={{ paddingBottom: '56px' }}>
          <AppRouter />
        </div>
        <BottomTabBar />
      </BrowserRouter>
    </AppRoot>
  )
}
