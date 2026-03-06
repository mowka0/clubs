import { useEffect } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { showBackButton, hideBackButton } from './sdk'

export function BackButtonHandler() {
  const navigate = useNavigate()
  const location = useLocation()

  const isRoot = location.pathname === '/'

  useEffect(() => {
    if (isRoot) {
      hideBackButton()
      return
    }

    const cleanup = showBackButton(() => navigate(-1))
    return () => {
      cleanup?.()
      hideBackButton()
    }
  }, [isRoot, navigate])

  return null
}
