import { init, retrieveLaunchParams, miniApp, viewport, backButton } from '@telegram-apps/sdk-react'

export type TelegramEnv = 'telegram' | 'mock'

let _env: TelegramEnv = 'mock'

export function getEnv(): TelegramEnv {
  return _env
}

export function isTelegramEnv(): boolean {
  return _env === 'telegram'
}

export function initTelegram(): void {
  try {
    init()
    _env = 'telegram'

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
  } catch {
    _env = 'mock'
  }
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
  if (!isTelegramEnv()) {
    return import.meta.env.VITE_MOCK_INIT_DATA ?? 'mock_init_data'
  }
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
