import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import '@telegram-apps/telegram-ui/dist/styles.css'
import { App } from './App'
import { initTelegram, mountBackButton } from './telegram/sdk'

initTelegram()
mountBackButton()

const rootEl = document.getElementById('root')
if (!rootEl) throw new Error('Root element not found')

createRoot(rootEl).render(
  <StrictMode>
    <App />
  </StrictMode>
)
