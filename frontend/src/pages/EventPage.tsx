import { useParams } from 'react-router-dom'

export function EventPage() {
  const { id } = useParams<{ id: string }>()
  return <div>Событие: {id}</div>
}
