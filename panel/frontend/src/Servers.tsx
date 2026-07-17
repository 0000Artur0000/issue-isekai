import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { api, can } from './api'
import { useAuth } from './auth'

type Server = {
  id: string
  name: string
  enabled: boolean
  createdAt: string
  lastSeenAt: string | null
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

const dateFormat = new Intl.DateTimeFormat(undefined, {
  dateStyle: 'medium',
  timeStyle: 'short',
})

function CopyButton({ value }: { value: string }) {
  const [copied, setCopied] = useState(false)
  return (
    <button
      type="button"
      onClick={() => navigator.clipboard.writeText(value).then(() => setCopied(true))}
    >
      {copied ? 'Скопировано' : 'Копировать'}
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
  if (!revisions) return <p role="status">Загрузка ревизий…</p>

  return (
    <section className="packs" aria-label={`Resource packs сервера ${server.name}`}>
      <h3>Resource packs — {server.name}</h3>
      {can(me, 'servers.packs.upload') && (
        <form className="filters" onSubmit={upload}>
          <input name="displayName" aria-label="Название ревизии" placeholder="Название" required />
          <input
            name="minecraftVersion"
            aria-label="Версия Minecraft"
            defaultValue="26.1.2"
            required
          />
          <input name="file" type="file" accept=".zip" aria-label="ZIP пака" required />
          <button type="submit" disabled={pending}>
            Загрузить
          </button>
          {uploadError && <p role="alert">{uploadError}</p>}
        </form>
      )}
      {revisions.length === 0 && <p>Ревизий ещё нет.</p>}
      {revisions.length > 0 && (
        <div className="table-scroll">
          <table>
            <caption className="visually-hidden">Ревизии resource pack</caption>
            <thead>
              <tr>
                <th scope="col">Название</th>
                <th scope="col">Minecraft / формат</th>
                <th scope="col">SHA-1 / SHA-256</th>
                <th scope="col">Загружена</th>
                <th scope="col">Активна</th>
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
                  <td>{dateFormat.format(new Date(revision.createdAt))}</td>
                  <td>
                    {revision.active ? (
                      'активна'
                    ) : can(me, 'servers.packs.activate') ? (
                      <button type="button" disabled={pending} onClick={() => activate(revision)}>
                        Сделать активной
                      </button>
                    ) : (
                      'нет'
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
  useEffect(load, [load])

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
    if (!window.confirm(`Перевыпустить ключ ${server.name}? Старый ключ перестанет работать.`)) {
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

  async function disable(server: Server) {
    if (!window.confirm(`Отключить сервер ${server.name}? Ingest перестанет приниматься.`)) return
    setPending(true)
    try {
      await api(`/api/admin/servers/${server.id}/disable`, { method: 'POST' })
      load()
    } catch (cause) {
      window.alert((cause as Error).message)
    } finally {
      setPending(false)
    }
  }

  if (error) return <p role="alert">{error}</p>
  if (!servers) return <p role="status">Загрузка…</p>

  return (
    <>
      <h1>Серверы</h1>
      {can(me, 'servers.create') && (
        <form className="filters" onSubmit={create}>
          <input name="name" aria-label="Имя сервера" placeholder="Имя сервера" required />
          <button type="submit" disabled={pending}>
            Создать
          </button>
          {formError && <p role="alert">{formError}</p>}
        </form>
      )}
      {credentials && (
        <div className="api-key" role="alert">
          <p>
            Ключ сервера <strong>{credentials.name}</strong> — показывается только один раз,
            сохраните его сейчас:
          </p>
          <p className="mono">{credentials.apiKey}</p>
          <CopyButton value={credentials.apiKey} />{' '}
          <button type="button" onClick={() => setCredentials(null)}>
            Скрыть
          </button>
        </div>
      )}
      {servers.length === 0 && <p>Серверов нет.</p>}
      {servers.length > 0 && (
        <div className="table-scroll">
          <table>
            <caption className="visually-hidden">Серверы Minecraft</caption>
            <thead>
              <tr>
                <th scope="col">Имя</th>
                <th scope="col">Статус</th>
                <th scope="col">Создан</th>
                <th scope="col">Последний ingest</th>
                <th scope="col">Действия</th>
              </tr>
            </thead>
            <tbody>
              {servers.map((server) => (
                <tr key={server.id}>
                  <td>{server.name}</td>
                  <td>{server.enabled ? 'активен' : 'отключён'}</td>
                  <td>{dateFormat.format(new Date(server.createdAt))}</td>
                  <td>
                    {server.lastSeenAt ? dateFormat.format(new Date(server.lastSeenAt)) : '—'}
                  </td>
                  <td className="actions">
                    {can(me, 'servers.keys.rotate') && (
                      <button type="button" disabled={pending} onClick={() => rotate(server)}>
                        Перевыпустить ключ
                      </button>
                    )}
                    {server.enabled && can(me, 'servers.state.update') && (
                      <button type="button" disabled={pending} onClick={() => disable(server)}>
                        Отключить
                      </button>
                    )}
                    {can(me, 'servers.packs.view') && (
                      <button
                        type="button"
                        onClick={() => setPacksFor(packsFor?.id === server.id ? null : server)}
                      >
                        {packsFor?.id === server.id ? 'Скрыть паки' : 'Паки'}
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
