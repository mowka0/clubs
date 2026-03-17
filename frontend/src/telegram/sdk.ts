import { init, retrieveLaunchParams, miniApp, viewport, backButton } from '@telegram-apps/sdk-react'

export type TelegramEnv = 'telegram' | 'mock' | 'webview'

let _env: TelegramEnv = 'mock'

// Direct access to Telegram WebView bridge (always injected by TG client)
declare global {
  interface Window {
    Telegram?: {
      WebApp?: {
        initData: string
        initDataUnsafe: Record<string, unknown>
        expand: () => void
        setHeaderColor: (color: string) => void
        ready: () => void
        version: string
        platform: string
      }
    }
  }
}

export function getEnv(): TelegramEnv {
  return _env
}

export function isTelegramEnv(): boolean {
  return _env === 'telegram' || _env === 'webview'
}

export function initTelegram(): void {
  // Diagnostic log
  const webApp = window.Telegram?.WebApp
  console.log('[TG] === INIT START ===')
  console.log('[TG] window.Telegram exists:', !!window.Telegram)
  console.log('[TG] window.Telegram.WebApp exists:', !!webApp)
  console.log('[TG] WebApp.initData:', webApp?.initData ? webApp.initData.substring(0, 100) + '...' : '(empty)')
  console.log('[TG] WebApp.version:', webApp?.version)
  console.log('[TG] WebApp.platform:', webApp?.platform)
  console.log('[TG] location.hash:', window.location.hash ? window.location.hash.substring(0, 100) : '(empty)')
  console.log('[TG] location.href:', window.location.href.substring(0, 150))

  // Strategy 1: Check Telegram.WebApp bridge FIRST (most reliable in ngrok scenario)
  // ngrok interstitial strips URL hash params, but Telegram.WebApp bridge is always injected
  if (webApp && webApp.initData && webApp.initData.length > 0) {
    _env = 'webview'
    console.log('[TG] Using Telegram.WebApp bridge, initData length:', webApp.initData.length)
    try { webApp.expand() } catch { /* ignore */ }
    try { webApp.setHeaderColor('bg_color') } catch { /* ignore */ }
    try { webApp.ready() } catch { /* ignore */ }
    return
  }

  // Strategy 2: SDK v3 (uses URL hash params — works when hash is preserved)
  try {
    init()
    _env = 'telegram'
    console.log('[TG] SDK v3 init OK')

    if (viewport.mount.isAvailable()) {
      void viewport.mount().then(() => {
        if (viewport.expand.isAvailable()) {
          viewport.expand()
        }
      })
    }

    if (miniApp.mount.isAvailable()) {
      void miniApp.mount().then(() => {
        if (miniApp.setHeaderColor.isAvailable()) {
          miniApp.setHeaderColor('bg_color')
        }
      })
    }
    return
  } catch (err) {
    console.warn('[TG] SDK v3 init failed:', err)
  }

  // No Telegram environment detected — mock mode (only for localhost dev)
  _env = 'mock'
  console.log('[TG] No Telegram env detected, using mock mode')
}

export function mountBackButton(): void {
  if (!isTelegramEnv()) return
  if (backButton.mount.isAvailable()) {
    backButton.mount()
  }
}

export function showBackButton(onBack: () => void): (() => void) | undefined {
  if (!isTelegramEnv()) return undefined
  if (backButton.show.isAvailable()) {
    backButton.show()
  }
  if (backButton.onClick.isAvailable()) {
    return backButton.onClick(onBack)
  }
  return undefined
}

export function hideBackButton(): void {
  if (!isTelegramEnv()) return
  if (backButton.hide.isAvailable()) {
    backButton.hide()
  }
}

export function getInitData(): string {
  if (_env === 'mock') {
    return import.meta.env.VITE_MOCK_INIT_DATA ?? 'mock_init_data'
  }

  // webview mode: use Telegram.WebApp bridge directly
  if (_env === 'webview') {
    const data = window.Telegram?.WebApp?.initData ?? ''
    console.log('[TG] getInitData (webview):', data ? data.substring(0, 80) + '...' : 'EMPTY')
    return data
  }

  // telegram mode: use SDK v3
  try {
    const params = retrieveLaunchParams()
    return (params.initDataRaw as string | undefined) ?? ''
  } catch {
    return ''
  }
}

export function getStartParam(): string | undefined {
  try {
    const params = retrieveLaunchParams()
    return params.startParam as string | undefined
  } catch {
    return import.meta.env.VITE_MOCK_START_PARAM
  }
}
