import { useState, type FormEvent } from 'react'
import {
  Navigate,
  NavLink,
  Outlet,
  Route,
  Routes,
  useLocation,
  useNavigate,
} from 'react-router-dom'
import { ApiError, login, logout } from './api'
import { useAuth } from './auth'

// ponytail: stub pages; real content arrives in frontend steps 3-5
function Stub({ title }: { title: string }) {
  return <h1>{title}</h1>
}

function Login() {
  const { me, setMe } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)
  const from = (location.state as { from?: { pathname: string } } | null)?.from?.pathname ?? '/board'

  if (me.authenticated) {
    return <Navigate to={from} replace />
  }

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    setPending(true)
    setError(null)
    try {
      setMe(await login(String(form.get('username')), String(form.get('password'))))
      navigate(from, { replace: true })
    } catch (cause) {
      setError(
        cause instanceof ApiError && cause.status === 401
          ? 'Неверное имя пользователя или пароль'
          : (cause as Error).message,
      )
    } finally {
      setPending(false)
    }
  }

  return (
    <main id="main">
      <form className="login" onSubmit={onSubmit}>
        <h1>Вход</h1>
        <label htmlFor="username">Имя пользователя</label>
        <input id="username" name="username" autoComplete="username" required />
        <label htmlFor="password">Пароль</label>
        <input
          id="password"
          name="password"
          type="password"
          autoComplete="current-password"
          required
        />
        {error && <p role="alert">{error}</p>}
        <button type="submit" disabled={pending}>
          {pending ? 'Вход…' : 'Войти'}
        </button>
      </form>
    </main>
  )
}

function Shell() {
  const { me, setMe } = useAuth()
  const location = useLocation()
  const [pending, setPending] = useState(false)

  if (!me.authenticated) {
    return <Navigate to="/login" replace state={{ from: location }} />
  }

  async function onLogout() {
    setPending(true)
    try {
      setMe(await logout())
    } catch (cause) {
      window.alert((cause as Error).message)
    } finally {
      setPending(false)
    }
  }

  return (
    <>
      <a className="skip-link" href="#main">
        К содержимому
      </a>
      <header>
        <nav aria-label="Основная навигация">
          <NavLink to="/board">Доска</NavLink>
          <NavLink to="/timeline">Лента</NavLink>
          {me.role === 'ADMIN' && (
            <>
              <NavLink to="/users">Пользователи</NavLink>
              <NavLink to="/servers">Серверы</NavLink>
            </>
          )}
        </nav>
        <span className="whoami">{me.username}</span>
        <button type="button" onClick={onLogout} disabled={pending}>
          Выйти
        </button>
      </header>
      <main id="main">
        <Outlet />
      </main>
    </>
  )
}

function RequireAdmin() {
  const { me } = useAuth()
  return me.role === 'ADMIN' ? <Outlet /> : <Navigate to="/board" replace />
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route element={<Shell />}>
        <Route path="/board" element={<Stub title="Доска" />} />
        <Route path="/timeline" element={<Stub title="Лента" />} />
        <Route path="/reports/:id" element={<Stub title="Заявка" />} />
        <Route element={<RequireAdmin />}>
          <Route path="/users" element={<Stub title="Пользователи" />} />
          <Route path="/servers" element={<Stub title="Серверы" />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/board" replace />} />
    </Routes>
  )
}
