import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { api, can, type RoleSummary } from './api'
import { useAuth } from './auth'

type User = {
  id: string
  username: string
  role: RoleSummary
  enabled: boolean
  createdAt: string
  updatedAt: string
}

const dateFormat = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' })

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
    if (user.enabled && !window.confirm(`Отключить пользователя ${user.username}?`)) return
    update(user, { enabled: !user.enabled })
  }

  function resetPassword(user: User) {
    const password = window.prompt(`Новый пароль для ${user.username}:`)
    if (password) update(user, { password })
  }

  if (error) return <p role="alert">{error}</p>
  if (!users) return <p role="status">Загрузка…</p>

  return (
    <>
      <h1>Пользователи</h1>
      {can(me, 'users.create') && canAssign && roles.length > 0 && (
        <form className="filters" onSubmit={create}>
          <input name="username" aria-label="Имя пользователя" placeholder="Имя" required />
          <input
            name="password"
            type="password"
            aria-label="Пароль"
            placeholder="Пароль"
            autoComplete="new-password"
            required
          />
          <select name="roleId" aria-label="Роль" required defaultValue="">
            <option value="" disabled>
              Выберите роль
            </option>
            {roles.map((role) => (
              <option key={role.id} value={role.id}>
                {role.displayName}
              </option>
            ))}
          </select>
          <button type="submit" disabled={pending}>
            Создать
          </button>
          {formError && <p role="alert">{formError}</p>}
        </form>
      )}
      {users.length === 0 && <p>Пользователей нет.</p>}
      {users.length > 0 && (
        <div className="table-scroll">
          <table>
            <caption className="visually-hidden">Пользователи панели</caption>
            <thead>
              <tr>
                <th scope="col">Имя</th>
                <th scope="col">Роль</th>
                <th scope="col">Статус</th>
                <th scope="col">Создан</th>
                <th scope="col">Действия</th>
              </tr>
            </thead>
            <tbody>
              {users.map((user) => (
                <tr key={user.id}>
                  <td>{user.username}</td>
                  <td>
                    {canAssign ? (
                      <select
                        aria-label={`Роль ${user.username}`}
                        value={user.role.id}
                        disabled={pending}
                        onChange={(event) => update(user, { roleId: event.target.value })}
                      >
                        {roles.map((role) => (
                          <option key={role.id} value={role.id}>
                            {role.displayName}
                          </option>
                        ))}
                      </select>
                    ) : (
                      user.role.displayName
                    )}
                  </td>
                  <td>{user.enabled ? 'активен' : 'отключён'}</td>
                  <td>{dateFormat.format(new Date(user.createdAt))}</td>
                  <td className="actions">
                    {can(me, 'users.state.update') && (
                      <button type="button" disabled={pending} onClick={() => toggle(user)}>
                        {user.enabled ? 'Отключить' : 'Включить'}
                      </button>
                    )}
                    {can(me, 'users.password.reset') && (
                      <button
                        type="button"
                        disabled={pending}
                        onClick={() => resetPassword(user)}
                      >
                        Сбросить пароль
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
