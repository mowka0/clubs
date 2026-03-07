import { AppRoot } from '@telegram-apps/telegram-ui'
import { BrowserRouter } from 'react-router-dom'
import { AppRouter } from './router'
import { BottomTabBar } from './components/BottomTabBar'
import { ErrorBoundary } from './components/ErrorBoundary'
import { BackButtonHandler } from './telegram/BackButtonHandler'
import { DeepLinkHandler } from './telegram/DeepLinkHandler'
import { AuthProvider } from './auth/AuthProvider'

export function App() {
  return (
    <ErrorBoundary>
      <AppRoot>
        <BrowserRouter>
          <AuthProvider>
            <BackButtonHandler />
            <DeepLinkHandler />
            <div style={{ paddingBottom: '56px' }}>
              <AppRouter />
            </div>
            <BottomTabBar />
          </AuthProvider>
        </BrowserRouter>
      </AppRoot>
    </ErrorBoundary>
  )
}
