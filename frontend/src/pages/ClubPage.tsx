import { useParams } from 'react-router-dom'

export function ClubPage() {
  const { id } = useParams<{ id: string }>()
  return <div>Клуб: {id}</div>
}
