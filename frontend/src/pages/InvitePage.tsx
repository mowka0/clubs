import { useParams } from 'react-router-dom'

export function InvitePage() {
  const { code } = useParams<{ code: string }>()
  return <div>Invite: {code}</div>
}
