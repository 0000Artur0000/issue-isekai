import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import { api, can, type RoleSummary } from './api'
import { useAuth } from './auth'

type Role = RoleSummary & {
  description: string
  permissions: string[]
  createdAt: string
  updatedAt: string
}

type RoleInput = { displayName: string; description: string; permissions: string[] }

function PermissionFields({
  permissions,
  selected,
  disabled,
}: {
  permissions: string[]
  selected: string[]
  disabled: boolean
}) {
  const groups = useMemo(
    () =>
      permissions.reduce<Record<string, string[]>>((result, permission) => {
        const group = permission.split('.')[0]
        ;(result[group] ??= []).push(permission)
        return result
      }, {}),
    [permissions],
  )
  return Object.entries(groups).map(([group, values]) => (
    <fieldset key={group} disabled={disabled}>
      <legend>{group}</legend>
      <div className="permission-grid">
        {values.map((permission) => (
          <label key={permission}>
            <input
              type="checkbox"
              name="permissions"
              value={permission}
              defaultChecked={selected.includes(permission)}
            />{' '}
            {permission}
          </label>
        ))}
      </div>
    </fieldset>
  ))
}

function RoleForm({
  role,
  permissions,
  editable,
  pending,
  onSubmit,
}: {
  role?: Role
  permissions: string[]
  editable: boolean
  pending: boolean
  onSubmit: (input: RoleInput, form: HTMLFormElement) => Promise<unknown>
}) {
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    const data = new FormData(form)
    await onSubmit(
      {
        displayName: String(data.get('displayName') ?? ''),
        description: String(data.get('description') ?? ''),
        permissions: data.getAll('permissions').map(String),
      },
      form,
    )
  }

  const immutable = role?.code === 'ADMIN'
  return (
    <form className="role-form" onSubmit={submit}>
      <label>
        Название
        <input
          name="displayName"
          defaultValue={role?.displayName ?? ''}
          disabled={!editable || immutable}
          readOnly={role?.code === 'OPERATOR'}
          required
          maxLength={100}
        />
      </label>
      <label>
        Описание
        <input
          name="description"
          defaultValue={role?.description ?? ''}
          disabled={!editable || immutable}
          maxLength={500}
        />
      </label>
      <PermissionFields
        permissions={permissions}
        selected={role?.permissions ?? []}
        disabled={!editable || immutable}
      />
      {editable && !immutable && (
        <button type="submit" disabled={pending}>
          {role ? 'Сохранить' : 'Создать роль'}
        </button>
      )}
    </form>
  )
}

export default function Roles() {
  const { me } = useAuth()
  const [roles, setRoles] = useState<Role[] | null>(null)
  const [permissions, setPermissions] = useState<string[]>([])
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  const load = useCallback(() => {
    setError(null)
    Promise.all([
      api<Role[]>('/api/admin/roles'),
      api<string[]>('/api/admin/permissions'),
    ]).then(
      ([loadedRoles, loadedPermissions]) => {
        setRoles(loadedRoles)
        setPermissions(loadedPermissions)
      },
      (cause: Error) => setError(cause.message),
    )
  }, [])
  useEffect(load, [load])

  async function save(path: string, method: 'POST' | 'PUT', input: RoleInput) {
    setPending(true)
    try {
      await api(path, { method, body: JSON.stringify(input) })
      load()
      return true
    } catch (cause) {
      window.alert((cause as Error).message)
      return false
    } finally {
      setPending(false)
    }
  }

  async function remove(role: Role) {
    if (!window.confirm(`Удалить роль ${role.displayName}?`)) return
    setPending(true)
    try {
      await api(`/api/admin/roles/${role.id}`, { method: 'DELETE' })
      load()
    } catch (cause) {
      window.alert((cause as Error).message)
    } finally {
      setPending(false)
    }
  }

  if (error) return <p role="alert">{error}</p>
  if (!roles) return <p role="status">Загрузка…</p>

  return (
    <>
      <h1>Роли и права</h1>
      {can(me, 'roles.create') && (
        <section className="role-card">
          <h2>Новая роль</h2>
          <RoleForm
            permissions={permissions}
            editable
            pending={pending}
            onSubmit={async (input, form) => {
              if (await save('/api/admin/roles', 'POST', input)) form.reset()
            }}
          />
        </section>
      )}
      {roles.length === 0 && <p>Ролей нет.</p>}
      {roles.map((role) => (
        <section className="role-card" key={`${role.id}-${role.updatedAt}`}>
          <h2>
            {role.displayName} <span className="meta">{role.code}</span>
          </h2>
          <RoleForm
            role={role}
            permissions={permissions}
            editable={can(me, 'roles.update')}
            pending={pending}
            onSubmit={(input) => save(`/api/admin/roles/${role.id}`, 'PUT', input)}
          />
          {!role.system && can(me, 'roles.delete') && (
            <button type="button" disabled={pending} onClick={() => remove(role)}>
              Удалить
            </button>
          )}
        </section>
      ))}
    </>
  )
}
