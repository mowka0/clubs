import { useAuthStore } from '../store/authStore'
import { useAuthContext } from '../auth/AuthProvider'

export function useAuth() {
  const { user, isLoading, isAuthenticated } = useAuthStore()
  const { logout } = useAuthContext()
  return { user, isLoading, isAuthenticated, logout }
}
