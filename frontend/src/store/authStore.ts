import { create } from 'zustand'

interface User {
  id: string
  telegramId: number
  username: string | null
  firstName: string | null
  lastName: string | null
  avatarUrl: string | null
}

interface AuthState {
  user: User | null
  token: string | null
  isLoading: boolean
  isAuthenticated: boolean
  setAuth: (user: User, token: string) => void
  clearAuth: () => void
  setLoading: (loading: boolean) => void
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  token: null,
  isLoading: true,
  isAuthenticated: false,
  setAuth: (user, token) => set({ user, token, isAuthenticated: true, isLoading: false }),
  clearAuth: () => set({ user: null, token: null, isAuthenticated: false }),
  setLoading: (loading) => set({ isLoading: loading }),
}))
