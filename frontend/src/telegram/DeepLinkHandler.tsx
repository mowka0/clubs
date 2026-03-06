import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { getStartParam } from './sdk'

export function DeepLinkHandler() {
  const navigate = useNavigate()

  useEffect(() => {
    const startParam = getStartParam()
    if (!startParam) return

    if (startParam.startsWith('invite_')) {
      const code = startParam.slice('invite_'.length)
      navigate(`/invite/${code}`, { replace: true })
    } else if (startParam.startsWith('event_')) {
      const id = startParam.slice('event_'.length)
      navigate(`/events/${id}`, { replace: true })
    } else if (startParam.startsWith('club_')) {
      const id = startParam.slice('club_'.length)
      navigate(`/clubs/${id}`, { replace: true })
    }
  }, [navigate])

  return null
}
