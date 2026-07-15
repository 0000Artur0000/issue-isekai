import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import { useParams } from 'react-router-dom'
import { api } from './api'
import { useAuth } from './auth'
import Inventory from './Inventory'
import { classifyUrls } from './media.mjs'
import {
  PRIORITY_LABELS,
  STATUS_LABELS,
  STATUSES,
  type Choice,
  type Choices,
  type Participant,
  type Priority,
  type Status,
} from './reports'

type Detail = {
  id: string
  serverName: string
  category: string
  description: string
  playerUuid: string
  playerName: string
  worldKey: string
  x: number
  y: number
  z: number
  gameMode: string
  reportedAt: string
  paperVersion: string
  status: Status
  priority: Priority
  assigneeId: string | null
  assigneeName: string | null
  duplicateOfId: string | null
  createdAt: string
  updatedAt: string
}

type AuditEvent = {
  id: number
  eventType: string
  actorName: string | null
  oldValue: string | null
  newValue: string | null
  createdAt: string
}

type Response = { report: Detail; events: AuditEvent[]; participants: Participant[] }

type MediaSegment = { type: string; text?: string; url?: string; embed?: string }

const dateTimeFormat = new Intl.DateTimeFormat(undefined, {
  dateStyle: 'medium',
  timeStyle: 'short',
})

function Media({ text }: { text: string }) {
  const segments = useMemo(() => classifyUrls(text) as MediaSegment[], [text])
  return (
    <div className="description">
      {segments.map((segment, index) => {
        switch (segment.type) {
          case 'image':
            return (
              <figure key={index}>
                <img src={segment.url} alt="" loading="lazy" referrerPolicy="no-referrer" />
                <SourceLink url={segment.url!} />
              </figure>
            )
          case 'video':
            return (
              <figure key={index}>
                {/* eslint-disable-next-line jsx-a11y/media-has-caption -- внешнее видео игрока */}
                <video src={segment.url} controls preload="none" />
                <SourceLink url={segment.url!} />
              </figure>
            )
          case 'youtube':
            return (
              <figure key={index}>
                <iframe
                  src={segment.embed}
                  title="YouTube"
                  sandbox="allow-scripts allow-same-origin allow-popups"
                  loading="lazy"
                  referrerPolicy="no-referrer"
                  allowFullScreen
                />
                <SourceLink url={segment.url!} />
              </figure>
            )
          case 'link':
            return (
              <a key={index} href={segment.url} target="_blank" rel="noopener noreferrer nofollow">
                {segment.url}
              </a>
            )
          default:
            return <span key={index}>{segment.text}</span>
        }
      })}
    </div>
  )
}

function SourceLink({ url }: { url: string }) {
  return (
    <figcaption>
      <a href={url} target="_blank" rel="noopener noreferrer nofollow">
        {url}
      </a>
    </figcaption>
  )
}

function WorkflowForm({ report, onSaved }: { report: Detail; onSaved: () => void }) {
  const [choices, setChoices] = useState<Choices | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  useEffect(() => {
    api<Choices>('/api/choices').then(setChoices, () => setChoices(null))
  }, [])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    setPending(true)
    setError(null)
    try {
      await api(`/api/reports/${report.id}`, {
        method: 'PUT',
        body: JSON.stringify({
          status: data.get('status'),
          priority: data.get('priority'),
          assigneeId: String(data.get('assigneeId') ?? '') || null,
          duplicateOfId: String(data.get('duplicateOfId') ?? '').trim() || null,
        }),
      })
      onSaved()
    } catch (cause) {
      setError((cause as Error).message)
    } finally {
      setPending(false)
    }
  }

  return (
    <form className="workflow" onSubmit={submit} key={report.updatedAt}>
      <label>
        Статус
        <select name="status" defaultValue={report.status}>
          {STATUSES.map((status) => (
            <option key={status} value={status}>
              {STATUS_LABELS[status]}
            </option>
          ))}
        </select>
      </label>
      <label>
        Приоритет
        <select name="priority" defaultValue={report.priority}>
          {Object.entries(PRIORITY_LABELS).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
      </label>
      <label>
        Ответственный
        <select name="assigneeId" defaultValue={report.assigneeId ?? ''}>
          <option value="">—</option>
          {choices?.assignees.map((assignee: Choice) => (
            <option key={assignee.id} value={assignee.id}>
              {assignee.name}
            </option>
          ))}
        </select>
      </label>
      <label>
        UUID оригинала (для дубликата)
        <input name="duplicateOfId" defaultValue={report.duplicateOfId ?? ''} />
      </label>
      {error && <p role="alert">{error}</p>}
      <button type="submit" disabled={pending}>
        Сохранить
      </button>
    </form>
  )
}

export default function Report() {
  const { id } = useParams<{ id: string }>()
  const { me } = useAuth()
  const [data, setData] = useState<Response | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)

  const load = useCallback(() => {
    api<Response>(`/api/reports/${id}`).then(setData, (cause: Error) => setError(cause.message))
  }, [id])

  useEffect(load, [load])

  if (error) return <p role="alert">{error}</p>
  if (!data) return <p role="status">Загрузка…</p>

  const { report, events, participants } = data
  const joined = participants.some((participant) => participant.name === me.username)

  async function toggleParticipation() {
    setPending(true)
    try {
      await api(`/api/reports/${report.id}/participants`, {
        method: joined ? 'DELETE' : 'POST',
      })
      load()
    } catch (cause) {
      window.alert((cause as Error).message)
    } finally {
      setPending(false)
    }
  }

  return (
    <article className="report-detail">
      <h1>
        {report.category}{' '}
        <span className={`badge status-${report.status}`}>{STATUS_LABELS[report.status]}</span>{' '}
        <span className={`badge priority-${report.priority}`}>
          {PRIORITY_LABELS[report.priority]}
        </span>
      </h1>
      <Media text={report.description} />
      <dl className="report-meta">
        <dt>Игрок</dt>
        <dd>
          {report.playerName} <span className="meta">{report.playerUuid}</span>
        </dd>
        <dt>Сервер</dt>
        <dd>
          {report.serverName} · {report.worldKey} · {report.x} {report.y} {report.z} ·{' '}
          {report.gameMode}
        </dd>
        <dt>Отправлено</dt>
        <dd>
          {dateTimeFormat.format(new Date(report.reportedAt))} · Paper {report.paperVersion}
        </dd>
        <dt>Ответственный</dt>
        <dd>{report.assigneeName ?? '—'}</dd>
        {report.duplicateOfId && (
          <>
            <dt>Дубликат</dt>
            <dd>
              <a href={`/reports/${report.duplicateOfId}`}>{report.duplicateOfId}</a>
            </dd>
          </>
        )}
        <dt>Участники</dt>
        <dd>
          {participants.length > 0
            ? participants.map((participant) => participant.name).join(', ')
            : '—'}{' '}
          <button type="button" onClick={toggleParticipation} disabled={pending}>
            {joined ? 'Покинуть' : 'Присоединиться'}
          </button>
        </dd>
      </dl>
      {me.role === 'ADMIN' && <WorkflowForm report={report} onSaved={load} />}
      <Inventory reportId={report.id} />
      <section aria-label="История">
        <h2>История</h2>
        <ul className="audit">
          {events.map((event) => (
            <li key={event.id}>
              <span className="meta">{dateTimeFormat.format(new Date(event.createdAt))}</span>{' '}
              {event.eventType} — {event.actorName ?? 'система'}
              {event.newValue && (
                <span className="meta">
                  {' '}
                  {event.oldValue ? `${event.oldValue} → ` : ''}
                  {event.newValue}
                </span>
              )}
            </li>
          ))}
        </ul>
      </section>
    </article>
  )
}
