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
      className="mc-btn mc-btn--sm"
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
          <input name="displayName" className="mc-input" aria-label={t('packs.revision-name')} placeholder={t('packs.name-placeholder')} required />
          <input
            name="minecraftVersion"
            className="mc-input"
            aria-label={t('packs.minecraft-version')}
            defaultValue="26.1.2"
            required
          />
          <input name="file" type="file" accept=".zip" aria-label={t('packs.file')} required />
          <button type="submit" className="mc-btn mc-btn--primary mc-btn--sm" disabled={pending}>
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
                      <span className="mc-chip status-RESOLVED">{t('packs.active-value')}</span>
                    ) : can(me, 'servers.packs.activate') ? (
                      <button type="button" className="mc-btn mc-btn--emerald mc-btn--sm" disabled={pending} onClick={() => activate(revision)}>
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

const SERVER_STATE: Record<Server['state'], { lamp: string; chip: string }> = {
  ONLINE: { lamp: 'block/redstone_lamp_on.png', chip: 'status-RESOLVED' },
  OFFLINE: { lamp: 'block/redstone_lamp.png', chip: 'status-REJECTED' },
  NEVER_CONNECTED: { lamp: 'block/lapis_block.png', chip: 'status-NEW' },
  DISABLED: { lamp: 'block/coal_block.png', chip: 'priority-LOW' },
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

  if (error)
    return (
      <div className="state-error mc-panel" role="alert">
        <img src="/assets/mc/item/barrier.png" alt="" />
        {error}
      </div>
    )
  if (!servers) return <p role="status" className="state-loading">{t('common.loading')}</p>

  return (
    <>
      <div className="page-head">
        <img src="/assets/mc/block/beacon.png" alt="" />
        <h1>{t('servers.title')}</h1>
      </div>
      {can(me, 'servers.create') && (
        <form className="create-bar" onSubmit={create}>
          <input
            name="name"
            className="mc-input"
            aria-label={t('servers.server-name')}
            placeholder={t('servers.server-name')}
            required
          />
          <button type="submit" className="mc-btn mc-btn--emerald mc-btn--sm" disabled={pending}>
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
          <button type="button" className="mc-btn mc-btn--sm" onClick={() => setCredentials(null)}>
            {t('common.hide')}
          </button>
        </div>
      )}
      {servers.length === 0 && (
        <div className="state-empty mc-panel">
          <img src="/assets/mc/big/beacon.png" alt="" />
          {t('servers.empty')}
        </div>
      )}
      {servers.length > 0 && (
        <div className="servers-grid">
          {servers.map((server) => {
            const visual = SERVER_STATE[server.state]
            const online = server.onlinePlayers ?? 0
            const max = server.maxPlayers ?? 0
            return (
              <section key={server.id} className="server-card mc-panel" aria-label={server.name}>
                <div className="server-head">
                  <img
                    className={`server-lamp${server.state === 'ONLINE' ? ' on' : ''}`}
                    src={`/assets/mc/${visual.lamp}`}
                    alt=""
                  />
                  <span className="server-name">{server.name}</span>
                  <span className={`mc-chip ${visual.chip}`}>{serverStateLabel(server.state)}</span>
                </div>
                <div className="server-stats">
                  <progress
                    className="mc-xp"
                    max={Math.max(max, 1)}
                    value={server.state === 'ONLINE' ? online : 0}
                    aria-label={`${online}/${max}`}
                  />
                  {server.state === 'ONLINE' ? `${online}/${max}` : t('common.none')}
                </div>
                <div className="server-times">
                  <span>
                    <img className="mc-ico mc-ico--sm" src="/assets/mc/item/clock_00.png" alt="" />{' '}
                    {t('servers.last-heartbeat')}:{' '}
                    {server.lastHeartbeatAt ? formatDate(server.lastHeartbeatAt) : t('common.none')}
                  </span>
                  <span>
                    <img className="mc-ico mc-ico--sm" src="/assets/mc/item/paper.png" alt="" />{' '}
                    {t('servers.last-report')}:{' '}
                    {server.lastReportAt ? formatDate(server.lastReportAt) : t('common.none')}
                  </span>
                  <span>
                    <img className="mc-ico mc-ico--sm" src="/assets/mc/block/gold_block.png" alt="" />{' '}
                    {t('common.created')}: {formatDate(server.createdAt)}
                  </span>
                </div>
                <div className="server-actions">
                  {can(me, 'servers.keys.rotate') && (
                    <button
                      type="button"
                      className="mc-btn mc-btn--gold mc-btn--sm"
                      disabled={pending}
                      onClick={() => rotate(server)}
                    >
                      {t('servers.rotate')}
                    </button>
                  )}
                  {can(me, 'servers.state.update') && (
                    <button
                      type="button"
                      className={`mc-btn mc-btn--sm ${server.enabled ? 'mc-btn--danger' : 'mc-btn--emerald'}`}
                      disabled={pending}
                      onClick={() => setEnabled(server, !server.enabled)}
                    >
                      {server.enabled ? t('common.disable') : t('common.enable')}
                    </button>
                  )}
                  {can(me, 'servers.packs.view') && (
                    <button
                      type="button"
                      className="mc-btn mc-btn--primary mc-btn--sm"
                      onClick={() => setPacksFor(packsFor?.id === server.id ? null : server)}
                    >
                      {packsFor?.id === server.id ? t('servers.hide-packs') : t('servers.show-packs')}
                    </button>
                  )}
                </div>
              </section>
            )
          })}
        </div>
      )}
      {packsFor && <Packs server={packsFor} />}
    </>
  )
}
