import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { api, can, type RoleSummary } from './api'
import { useAuth } from './auth'
import { formatDate, roleLabel, t } from './i18n'

type User = {
  id: string
  username: string
  role: RoleSummary
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export default function Users() {
  const { me } = useAuth()
  const [users, setUsers] = useState<User[] | null>(null)
  const [roles, setRoles] = useState<RoleSummary[]>([])
  const [error, setError] = useState<string | null>(null)
  const [formError, setFormError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)
  const canAssign = can(me, 'users.role.assign')

  const load = useCallback(() => {
    Promise.all([
      api<User[]>('/api/admin/users'),
      canAssign ? api<RoleSummary[]>('/api/admin/roles') : Promise.resolve([]),
    ]).then(
      ([loadedUsers, loadedRoles]) => {
        setUsers(loadedUsers)
        setRoles(loadedRoles)
      },
      (cause: Error) => setError(cause.message),
    )
  }, [canAssign])
  useEffect(load, [load])

  // ponytail: пароль живёт только в форме/аргументах, в state не попадает
  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    const data = new FormData(form)
    setPending(true)
    setFormError(null)
    try {
      await api('/api/admin/users', {
        method: 'POST',
        body: JSON.stringify({
          username: data.get('username'),
          password: data.get('password'),
          roleId: data.get('roleId'),
        }),
      })
      form.reset()
      load()
    } catch (cause) {
      setFormError((cause as Error).message)
    } finally {
      setPending(false)
    }
  }

  async function update(
    user: User,
    patch: { roleId?: string; enabled?: boolean; password?: string },
  ) {
    setPending(true)
    try {
      await api(`/api/admin/users/${user.id}`, {
        method: 'PUT',
        body: JSON.stringify({
          roleId: patch.roleId ?? user.role.id,
          enabled: patch.enabled ?? user.enabled,
          password: patch.password ?? null,
        }),
      })
      load()
    } catch (cause) {
      window.alert((cause as Error).message)
    } finally {
      setPending(false)
    }
  }

  function toggle(user: User) {
    if (user.enabled && !window.confirm(t('users.disable-confirm', { name: user.username }))) return
    update(user, { enabled: !user.enabled })
  }

  function resetPassword(user: User) {
    const password = window.prompt(t('users.password-prompt', { name: user.username }))
    if (password) update(user, { password })
  }

  if (error)
    return (
      <div className="state-error mc-panel" role="alert">
        <img src="/assets/mc/item/barrier.png" alt="" />
        {error}
      </div>
    )
  if (!users) return <p role="status" className="state-loading">{t('common.loading')}</p>

  return (
    <>
      <div className="page-head">
        <img src="/assets/mc/item/name_tag.png" alt="" />
        <h1>{t('users.title')}</h1>
      </div>
      {can(me, 'users.create') && canAssign && roles.length > 0 && (
        <form className="create-bar" onSubmit={create}>
          <input name="username" className="mc-input" aria-label={t('users.username')} placeholder={t('users.username-placeholder')} required />
          <input
            name="password"
            type="password"
            className="mc-input"
            aria-label={t('users.password')}
            placeholder={t('users.password')}
            autoComplete="new-password"
            required
          />
          <select name="roleId" className="mc-input" aria-label={t('users.role')} required defaultValue="">
            <option value="" disabled>
              {t('users.choose-role')}
            </option>
            {roles.map((role) => (
              <option key={role.id} value={role.id}>
                {roleLabel(role.code, role.displayName)}
              </option>
            ))}
          </select>
          <button type="submit" className="mc-btn mc-btn--emerald mc-btn--sm" disabled={pending}>
            {t('common.create')}
          </button>
          {formError && <p role="alert">{formError}</p>}
        </form>
      )}
      {users.length === 0 && (
        <div className="state-empty mc-panel">
          <img src="/assets/mc/big/name_tag.png" alt="" />
          {t('users.empty')}
        </div>
      )}
      {users.length > 0 && (
        <div className="table-scroll">
          <table>
            <caption className="visually-hidden">{t('users.table')}</caption>
            <thead>
              <tr>
                <th scope="col">{t('common.name')}</th>
                <th scope="col">{t('users.role')}</th>
                <th scope="col">{t('common.status')}</th>
                <th scope="col">{t('common.created')}</th>
                <th scope="col">{t('common.actions')}</th>
              </tr>
            </thead>
            <tbody>
              {users.map((user) => (
                <tr key={user.id}>
                  <td>
                    <span className="user-cell">
                      <img
                        src={`/assets/mc/${user.role.code === 'ADMIN' ? 'item/nether_star' : 'item/name_tag'}.png`}
                        alt=""
                      />
                      {user.username}
                    </span>
                  </td>
                  <td>
                    {canAssign ? (
                      <select
                        className="mc-input"
                        aria-label={t('users.role-label', { name: user.username })}
                        value={user.role.id}
                        disabled={pending}
                        onChange={(event) => update(user, { roleId: event.target.value })}
                      >
                        {roles.map((role) => (
                          <option key={role.id} value={role.id}>
                            {roleLabel(role.code, role.displayName)}
                          </option>
                        ))}
                      </select>
                    ) : (
                      roleLabel(user.role.code, user.role.displayName)
                    )}
                  </td>
                  <td>
                    <span className={`mc-chip ${user.enabled ? 'status-RESOLVED' : 'status-REJECTED'}`}>
                      {user.enabled ? t('users.active') : t('users.disabled')}
                    </span>
                  </td>
                  <td>{formatDate(user.createdAt, 'date')}</td>
                  <td className="actions">
                    {can(me, 'users.state.update') && (
                      <button
                        type="button"
                        className={`mc-btn mc-btn--sm ${user.enabled ? 'mc-btn--danger' : 'mc-btn--emerald'}`}
                        disabled={pending}
                        onClick={() => toggle(user)}
                      >
                        {user.enabled ? t('common.disable') : t('common.enable')}
                      </button>
                    )}
                    {can(me, 'users.password.reset') && (
                      <button
                        type="button"
                        className="mc-btn mc-btn--gold mc-btn--sm"
                        disabled={pending}
                        onClick={() => resetPassword(user)}
                      >
                        {t('users.reset-password')}
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  )
}
