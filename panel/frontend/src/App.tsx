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
import { ApiError, can, login, logout } from './api'
import { useAuth } from './auth'
import { t } from './i18n'
import Board from './Board'
import Report from './Report'
import Roles from './Roles'
import Servers from './Servers'
import Timeline from './Timeline'
import Users from './Users'

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
          ? t('login.invalid')
          : (cause as Error).message,
      )
    } finally {
      setPending(false)
    }
  }

  return (
    <main id="main">
      <form className="login" onSubmit={onSubmit}>
        <h1>{t('login.title')}</h1>
        <label htmlFor="username">{t('login.username')}</label>
        <input id="username" name="username" autoComplete="username" required />
        <label htmlFor="password">{t('login.password')}</label>
        <input
          id="password"
          name="password"
          type="password"
          autoComplete="current-password"
          required
        />
        {error && <p role="alert">{error}</p>}
        <button type="submit" disabled={pending}>
          {pending ? t('login.pending') : t('login.submit')}
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
        {t('nav.skip')}
      </a>
      <header>
        <nav aria-label={t('nav.main')}>
          {can(me, 'reports.view') && <NavLink to="/board">{t('nav.board')}</NavLink>}
          {can(me, 'reports.view') && <NavLink to="/timeline">{t('nav.timeline')}</NavLink>}
          {can(me, 'users.view') && <NavLink to="/users">{t('nav.users')}</NavLink>}
          {can(me, 'roles.view') && <NavLink to="/roles">{t('nav.roles')}</NavLink>}
          {can(me, 'servers.view') && <NavLink to="/servers">{t('nav.servers')}</NavLink>}
        </nav>
        <span className="whoami">{me.username}</span>
        <button type="button" onClick={onLogout} disabled={pending}>
          {t('nav.logout')}
        </button>
      </header>
      <main id="main">
        <Outlet />
      </main>
    </>
  )
}

function RequirePermission({ permission }: { permission: string }) {
  const { me } = useAuth()
  if (!can(me, permission)) {
    return <p role="alert">{t('auth.denied')}</p>
  }
  return <Outlet />
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route element={<Shell />}>
        <Route element={<RequirePermission permission="reports.view" />}>
          <Route path="/board" element={<Board />} />
          <Route path="/timeline" element={<Timeline />} />
          <Route path="/reports/:id" element={<Report />} />
        </Route>
        <Route element={<RequirePermission permission="users.view" />}>
          <Route path="/users" element={<Users />} />
        </Route>
        <Route element={<RequirePermission permission="roles.view" />}>
          <Route path="/roles" element={<Roles />} />
        </Route>
        <Route element={<RequirePermission permission="servers.view" />}>
          <Route path="/servers" element={<Servers />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/board" replace />} />
    </Routes>
  )
}
