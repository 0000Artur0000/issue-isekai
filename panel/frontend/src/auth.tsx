import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { fetchMe, type Me } from './api'

type Auth = { me: Me; setMe: (me: Me) => void }

const AuthContext = createContext<Auth | null>(null)

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): Auth {
  return useContext(AuthContext)!
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [me, setMe] = useState<Me | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchMe().then(setMe, (cause: Error) => setError(cause.message))
  }, [])

  if (error) {
    return (
      <main>
        <p role="alert">Не удалось загрузить сессию: {error}</p>
        <button type="button" onClick={() => window.location.reload()}>
          Повторить
        </button>
      </main>
    )
  }
  if (!me) {
    return (
      <main>
        <p role="status">Загрузка…</p>
      </main>
    )
  }
  return <AuthContext.Provider value={{ me, setMe }}>{children}</AuthContext.Provider>
}
