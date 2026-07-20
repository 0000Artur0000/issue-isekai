import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { api, can } from './api'
import { useAuth } from './auth'
import { formatDate, serverStateLabel, t } from './i18n'

type Server = {
  id: string
  name: string
  enabled: boolean
  state: 'DISABLED' | 'NEVER_CONNECTED' | 'ONLINE' | 'OFFLINE'
  onlinePlayers: number | null
  maxPlayers: number | null
  createdAt: string
  lastReportAt: string | null
  lastHeartbeatAt: string | null
}

type Revision = {
  id: string
  displayName: string
  minecraftVersion: string
  packFormatMin: number
  packFormatMax: number
  sha1: string
  sha256: string
  sizeBytes: number
  active: boolean
  createdAt: string
}

function CopyButton({ value }: { value: string }) {
  const [copied, setCopied] = useState(false)
  return (
    <button
      type="button"
      onClick={() => navigator.clipboard.writeText(value).then(() => setCopied(true))}
    >
      {copied ? t('common.copied') : t('common.copy')}
    </button>
  )
}

function Packs({ server }: { server: Server }) {
  const { me } = useAuth()
  const [revisions, setRevisions] = useState<Revision[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [uploadError, setUploadError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  const load = useCallback(() => {
    api<Revision[]>(`/api/admin/servers/${server.id}/resource-packs`).then(setRevisions, (cause: Error) =>
      setError(cause.message),
    )
  }, [server.id])
  useEffect(load, [load])

  // ponytail: файл уходит в FormData как есть — JS его не читает и не превьюит
  async function upload(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    setPending(true)
    setUploadError(null)
    try {
      await api(`/api/admin/servers/${server.id}/resource-packs`, {
        method: 'POST',
        body: new FormData(form),
      })
      form.reset()
      load()
    } catch (cause) {
      setUploadError((cause as Error).message)
    } finally {
      setPending(false)
    }
  }

  async function activate(revision: Revision) {
    setPending(true)
    try {
      await api(`/api/admin/servers/${server.id}/resource-packs/${revision.id}/active`, {
        method: 'PUT',
      })
      load()
    } catch (cause) {
      window.alert((cause as Error).message)
    } finally {
      setPending(false)
    }
  }

  if (error) return <p role="alert">{error}</p>
  if (!revisions) return <p role="status">{t('packs.loading')}</p>

  return (
    <section className="packs" aria-label={t('packs.section', { name: server.name })}>
      <h3>{t('packs.title', { name: server.name })}</h3>
      {can(me, 'servers.packs.upload') && (
        <form className="filters" onSubmit={upload}>
          <input name="displayName" aria-label={t('packs.revision-name')} placeholder={t('packs.name-placeholder')} required />
          <input
            name="minecraftVersion"
            aria-label={t('packs.minecraft-version')}
            defaultValue="26.1.2"
            required
          />
          <input name="file" type="file" accept=".zip" aria-label={t('packs.file')} required />
          <button type="submit" disabled={pending}>
            {t('common.upload')}
          </button>
          {uploadError && <p role="alert">{uploadError}</p>}
        </form>
      )}
      {revisions.length === 0 && <p>{t('packs.empty')}</p>}
      {revisions.length > 0 && (
        <div className="table-scroll">
          <table>
            <caption className="visually-hidden">{t('packs.table')}</caption>
            <thead>
              <tr>
                <th scope="col">{t('packs.name')}</th>
                <th scope="col">{t('packs.format')}</th>
                <th scope="col">{t('packs.hashes')}</th>
                <th scope="col">{t('packs.uploaded')}</th>
                <th scope="col">{t('packs.active')}</th>
              </tr>
            </thead>
            <tbody>
              {revisions.map((revision) => (
                <tr key={revision.id}>
                  <td>{revision.displayName}</td>
                  <td>
                    {revision.minecraftVersion} · {revision.packFormatMin}
                    {revision.packFormatMax !== revision.packFormatMin
                      ? `–${revision.packFormatMax}`
                      : ''}
                  </td>
                  <td className="mono hashes">
                    {revision.sha1}
                    <br />
                    {revision.sha256}
                  </td>
                  <td>{formatDate(revision.createdAt)}</td>
                  <td>
                    {revision.active ? (
                      t('packs.active-value')
                    ) : can(me, 'servers.packs.activate') ? (
                      <button type="button" disabled={pending} onClick={() => activate(revision)}>
                        {t('packs.activate')}
                      </button>
                    ) : (
                      t('common.no')
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}

export default function Servers() {
  const { me } = useAuth()
  const [servers, setServers] = useState<Server[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [formError, setFormError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)
  const [credentials, setCredentials] = useState<{ name: string; apiKey: string } | null>(null)
  const [packsFor, setPacksFor] = useState<Server | null>(null)

  const load = useCallback(() => {
    api<Server[]>('/api/admin/servers').then(setServers, (cause: Error) => setError(cause.message))
  }, [])
  useEffect(() => {
    const refresh = () => {
      if (document.visibilityState === 'visible') load()
    }
    refresh()
    const timer = window.setInterval(refresh, 30_000)
    document.addEventListener('visibilitychange', refresh)
    return () => {
      window.clearInterval(timer)
      document.removeEventListener('visibilitychange', refresh)
    }
  }, [load])

  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    const name = String(new FormData(form).get('name') ?? '')
    setPending(true)
    setFormError(null)
    try {
      const created = await api<{ id: string; name: string; apiKey: string }>(
        '/api/admin/servers',
        { method: 'POST', body: JSON.stringify({ name }) },
      )
      setCredentials({ name: created.name, apiKey: created.apiKey })
      form.reset()
      load()
    } catch (cause) {
      setFormError((cause as Error).message)
    } finally {
      setPending(false)
    }
  }

  async function rotate(server: Server) {
    if (!window.confirm(t('servers.rotate-confirm', { name: server.name }))) {
      return
    }
    setPending(true)
    try {
      const rotated = await api<{ apiKey: string }>(`/api/admin/servers/${server.id}/rotate`, {
        method: 'POST',
      })
      setCredentials({ name: server.name, apiKey: rotated.apiKey })
    } catch (cause) {
      window.alert((cause as Error).message)
    } finally {
      setPending(false)
    }
  }

  async function setEnabled(server: Server, enabled: boolean) {
    if (
      server.enabled &&
      !window.confirm(t('servers.disable-confirm', { name: server.name }))
    )
      return
    setPending(true)
    try {
      await api(`/api/admin/servers/${server.id}/${enabled ? 'enable' : 'disable'}`, {
        method: 'POST',
      })
      load()
    } catch (cause) {
      window.alert((cause as Error).message)
    } finally {
      setPending(false)
    }
  }

  if (error) return <p role="alert">{error}</p>
  if (!servers) return <p role="status">{t('common.loading')}</p>

  return (
    <>
      <h1>{t('servers.title')}</h1>
      {can(me, 'servers.create') && (
        <form className="filters" onSubmit={create}>
          <input name="name" aria-label={t('servers.server-name')} placeholder={t('servers.server-name')} required />
          <button type="submit" disabled={pending}>
            {t('common.create')}
          </button>
          {formError && <p role="alert">{formError}</p>}
        </form>
      )}
      {credentials && (
        <div className="api-key" role="alert">
          <p>
            {t('servers.key-once', { name: credentials.name })}
          </p>
          <p className="mono">{credentials.apiKey}</p>
          <CopyButton value={credentials.apiKey} />{' '}
          <button type="button" onClick={() => setCredentials(null)}>
            {t('common.hide')}
          </button>
        </div>
      )}
      {servers.length === 0 && <p>{t('servers.empty')}</p>}
      {servers.length > 0 && (
        <div className="table-scroll">
          <table>
            <caption className="visually-hidden">{t('servers.table')}</caption>
            <thead>
              <tr>
                <th scope="col">{t('common.name')}</th>
                <th scope="col">{t('common.status')}</th>
                <th scope="col">{t('servers.players')}</th>
                <th scope="col">{t('common.created')}</th>
                <th scope="col">{t('servers.last-heartbeat')}</th>
                <th scope="col">{t('servers.last-report')}</th>
                <th scope="col">{t('common.actions')}</th>
              </tr>
            </thead>
            <tbody>
              {servers.map((server) => (
                <tr key={server.id}>
                  <td>{server.name}</td>
                  <td>
                    <span className={`badge server-${server.state.toLowerCase()}`}>
                      {serverStateLabel(server.state)}
                    </span>
                  </td>
                  <td>
                    {server.state === 'ONLINE'
                      ? `${server.onlinePlayers ?? 0}/${server.maxPlayers ?? 0}`
                      : t('common.none')}
                  </td>
                  <td>{formatDate(server.createdAt)}</td>
                  <td>
                    {server.lastHeartbeatAt
                      ? formatDate(server.lastHeartbeatAt)
                      : t('common.none')}
                  </td>
                  <td>
                    {server.lastReportAt ? formatDate(server.lastReportAt) : t('common.none')}
                  </td>
                  <td className="actions">
                    {can(me, 'servers.keys.rotate') && (
                      <button type="button" disabled={pending} onClick={() => rotate(server)}>
                        {t('servers.rotate')}
                      </button>
                    )}
                    {can(me, 'servers.state.update') && (
                      <button
                        type="button"
                        disabled={pending}
                        onClick={() => setEnabled(server, !server.enabled)}
                      >
                        {server.enabled ? t('common.disable') : t('common.enable')}
                      </button>
                    )}
                    {can(me, 'servers.packs.view') && (
                      <button
                        type="button"
                        onClick={() => setPacksFor(packsFor?.id === server.id ? null : server)}
                      >
                        {packsFor?.id === server.id ? t('servers.hide-packs') : t('servers.show-packs')}
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {packsFor && <Packs server={packsFor} />}
    </>
  )
}
