import { lazy, Suspense, type ReactNode } from 'react'
import { Routes, Route } from 'react-router-dom'

import { DiscoveryPage } from './pages/DiscoveryPage'
import { MyClubsPage } from './pages/MyClubsPage'
import { ProfilePage } from './pages/ProfilePage'
import { ClubPage } from './pages/ClubPage'

// Code-split heavy pages
const ClubInteriorPage = lazy(() =>
  import('./pages/ClubInteriorPage').then((m) => ({ default: m.ClubInteriorPage }))
)
const OrganizerPage = lazy(() =>
  import('./pages/OrganizerPage').then((m) => ({ default: m.OrganizerPage }))
)
const EventPage = lazy(() =>
  import('./pages/EventPage').then((m) => ({ default: m.EventPage }))
)
const InvitePage = lazy(() =>
  import('./pages/InvitePage').then((m) => ({ default: m.InvitePage }))
)

function PageSuspense({ children }: { children: ReactNode }) {
  return <Suspense fallback={<div style={{ padding: '16px' }}>Загрузка...</div>}>{children}</Suspense>
}

export function AppRouter() {
  return (
    <Routes>
      <Route path="/" element={<DiscoveryPage />} />
      <Route path="/my-clubs" element={<MyClubsPage />} />
      <Route path="/profile" element={<ProfilePage />} />
      <Route path="/clubs/:id" element={<ClubPage />} />
      <Route
        path="/clubs/:id/interior"
        element={
          <PageSuspense>
            <ClubInteriorPage />
          </PageSuspense>
        }
      />
      <Route
        path="/organizer"
        element={
          <PageSuspense>
            <OrganizerPage />
          </PageSuspense>
        }
      />
      <Route
        path="/events/:id"
        element={
          <PageSuspense>
            <EventPage />
          </PageSuspense>
        }
      />
      <Route
        path="/invite/:code"
        element={
          <PageSuspense>
            <InvitePage />
          </PageSuspense>
        }
      />
    </Routes>
  )
}
