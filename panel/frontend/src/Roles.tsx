import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import { api, can, type RoleSummary } from './api'
import { useAuth } from './auth'
import {
  permissionGroupLabel,
  permissionLabel,
  roleLabel,
  t,
} from './i18n'

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
    <fieldset key={group} className="perm-group" disabled={disabled}>
      <legend>{permissionGroupLabel(group)}</legend>
      <div className="permission-grid">
        {values.map((permission) => (
          <label key={permission} className="perm-check">
            <input
              type="checkbox"
              name="permissions"
              value={permission}
              defaultChecked={selected.includes(permission)}
            />
            <span>
              {permissionLabel(permission)} <span className="perm-code">{permission}</span>
            </span>
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
        {t('roles.name')}
        <input
          name="displayName"
          className="mc-input"
          defaultValue={role?.displayName ?? ''}
          disabled={!editable || immutable}
          readOnly={role?.code === 'OPERATOR'}
          required
          maxLength={100}
        />
      </label>
      <label>
        {t('roles.description')}
        <input
          name="description"
          className="mc-input"
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
        <div>
          <button type="submit" className="mc-btn mc-btn--gold" disabled={pending}>
            {role ? t('common.save') : t('roles.create')}
          </button>
        </div>
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
    if (!window.confirm(t('roles.remove-confirm', { name: roleLabel(role.code, role.displayName) }))) return
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

  if (error)
    return (
      <div className="state-error mc-panel" role="alert">
        <img src="/assets/mc/item/barrier.png" alt="" />
        {error}
      </div>
    )
  if (!roles) return <p role="status" className="state-loading">{t('common.loading')}</p>

  return (
    <>
      <div className="page-head">
        <img src="/assets/mc/item/armor_stand.png" alt="" />
        <h1>{t('roles.title')}</h1>
      </div>
      {can(me, 'roles.create') && (
        <section className="role-card mc-panel">
          <h2>{t('roles.new')}</h2>
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
      {roles.length === 0 && (
        <div className="state-empty mc-panel">
          <img src="/assets/mc/big/armor_stand.png" alt="" />
          {t('roles.empty')}
        </div>
      )}
      {roles.map((role) => (
        <section className={`role-card mc-panel${role.code === 'ADMIN' ? ' mc-panel--gold' : ''}`} key={`${role.id}-${role.updatedAt}`}>
          <div className="role-head">
            {role.code === 'ADMIN' && (
              <img className="role-seal" src="/assets/mc/item/nether_star.png" alt="" />
            )}
            {role.system && role.code !== 'ADMIN' && (
              <img className="role-seal" src="/assets/mc/big/command_block_front.png" alt="" />
            )}
            <h2>{roleLabel(role.code, role.displayName)}</h2>
            <span className="role-code">{role.code}</span>
          </div>
          <RoleForm
            role={role}
            permissions={permissions}
            editable={can(me, 'roles.update')}
            pending={pending}
            onSubmit={(input) => save(`/api/admin/roles/${role.id}`, 'PUT', input)}
          />
          {!role.system && can(me, 'roles.delete') && (
            <button type="button" className="mc-btn mc-btn--danger mc-btn--sm" disabled={pending} onClick={() => remove(role)}>
              {t('common.delete')}
            </button>
          )}
        </section>
      ))}
    </>
  )
}
