import { createContext, useContext, useEffect, useRef, type ReactNode } from 'react'
import { useAuthStore } from '../store/authStore'
import { authenticate } from '../api/apiClient'

interface AuthContextValue {
  logout: () => void
}

const AuthContext = createContext<AuthContextValue>({ logout: () => {} })

export function AuthProvider({ children }: { children: ReactNode }) {
  const { setLoading, clearAuth } = useAuthStore()
  const initialized = useRef(false)

  useEffect(() => {
    if (initialized.current) return
    initialized.current = true

    setLoading(true)
    authenticate().finally(() => {
      setLoading(false)
    })
  }, [setLoading])

  function logout() {
    clearAuth()
  }

  return <AuthContext.Provider value={{ logout }}>{children}</AuthContext.Provider>
}

export function useAuthContext() {
  return useContext(AuthContext)
}
