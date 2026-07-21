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

const MC = '/assets/mc'

function Particles() {
  const kinds = ['portal', 'glint', 'hit']
  return (
    <div className="fx-field" aria-hidden="true">
      {Array.from({ length: 12 }, (_, index) => (
        <span key={index} className={`fx-p fx-p--${kinds[index % 3]} n${index + 1}`} />
      ))}
    </div>
  )
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
          ? t('login.invalid')
          : (cause as Error).message,
      )
    } finally {
      setPending(false)
    }
  }

  return (
    <main id="main" className="login-wrap">
      <Particles />
      <div className="login-card mc-frame--amethyst mc-frame">
        <div className="login-logo">
          <img src={`${MC}/big/nether_star.png`} alt="" />
          <h1>
            Issue<b>Isekai</b>
          </h1>
        </div>
        <p className="login-sub">{t('login.title')}</p>
        <form className="login" onSubmit={onSubmit}>
          <label htmlFor="username">{t('login.username')}</label>
          <input id="username" name="username" className="mc-input" autoComplete="username" required />
          <label htmlFor="password">{t('login.password')}</label>
          <input
            id="password"
            name="password"
            type="password"
            className="mc-input"
            autoComplete="current-password"
            required
          />
          {error && <p role="alert">{error}</p>}
          <button type="submit" className="mc-btn mc-btn--emerald" disabled={pending}>
            {pending ? t('login.pending') : t('login.submit')}
          </button>
        </form>
        <p className="login-foot">Minecraft fan project · not affiliated with Mojang</p>
      </div>
    </main>
  )
}

const NAV_ITEMS = [
  { to: '/board', permission: 'reports.view', label: 'nav.board', icon: 'item/writable_book.png' },
  { to: '/timeline', permission: 'reports.view', label: 'nav.timeline', icon: 'item/clock_00.png' },
  { to: '/users', permission: 'users.view', label: 'nav.users', icon: 'item/name_tag.png' },
  { to: '/roles', permission: 'roles.view', label: 'nav.roles', icon: 'item/armor_stand.png' },
  { to: '/servers', permission: 'servers.view', label: 'nav.servers', icon: 'block/beacon.png' },
] as const

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
      <header className="app-header">
        <NavLink to="/board" className="brand">
          <img src={`${MC}/item/amethyst_shard.png`} alt="" />
          <span>
            Issue<b>Isekai</b>
          </span>
        </NavLink>
        <nav className="app-nav" aria-label={t('nav.main')}>
          {NAV_ITEMS.filter((item) => can(me, item.permission)).map((item) => (
            <NavLink key={item.to} to={item.to}>
              <img className="mc-ico" src={`${MC}/${item.icon}`} alt="" />
              <span className="nav-label">{t(item.label)}</span>
            </NavLink>
          ))}
        </nav>
        <span className="header-user">
          <img className="mc-ico" src={`${MC}/item/name_tag.png`} alt="" />
          <span className="whoami">{me.username}</span>
        </span>
        <button type="button" className="mc-btn mc-btn--danger mc-btn--sm" onClick={onLogout} disabled={pending}>
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
