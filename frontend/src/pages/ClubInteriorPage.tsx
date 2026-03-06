import { useParams } from 'react-router-dom'

export function ClubInteriorPage() {
  const { id } = useParams<{ id: string }>()
  return <div>Внутренний экран клуба: {id}</div>
}
