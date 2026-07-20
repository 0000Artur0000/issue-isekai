import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import { useParams } from 'react-router-dom'
import { api, can } from './api'
import { useAuth } from './auth'
import { eventLabel, formatDate, priorityLabel, statusLabel, t } from './i18n'
import Inventory from './Inventory'
import { classifyUrls } from './media.mjs'
import {
  PRIORITIES,
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
  const { me } = useAuth()
  const [choices, setChoices] = useState<Choices | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)
  const canDuplicate = can(me, 'reports.duplicate.update')

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
          status: data.get('status') ?? report.status,
          priority: data.get('priority') ?? report.priority,
          assigneeId: data.has('assigneeId')
            ? String(data.get('assigneeId') ?? '') || null
            : report.assigneeId,
          duplicateOfId: data.has('duplicateOfId')
            ? String(data.get('duplicateOfId') ?? '').trim() || null
            : report.duplicateOfId,
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
        {t('report.form-status')}
        <select
          name="status"
          defaultValue={report.status}
          disabled={
            !can(me, 'reports.status.update') ||
            (report.status === 'DUPLICATE' && !canDuplicate)
          }
        >
          {STATUSES.map((status) => (
            <option key={status} value={status} disabled={status === 'DUPLICATE' && !canDuplicate}>
              {statusLabel(status)}
            </option>
          ))}
        </select>
      </label>
      <label>
        {t('report.form-priority')}
        <select
          name="priority"
          defaultValue={report.priority}
          disabled={!can(me, 'reports.priority.update')}
        >
          {PRIORITIES.map((value) => (
            <option key={value} value={value}>
              {priorityLabel(value)}
            </option>
          ))}
        </select>
      </label>
      <label>
        {t('report.form-assignee')}
        <select
          name="assigneeId"
          defaultValue={report.assigneeId ?? ''}
          disabled={!can(me, 'reports.assignee.update')}
        >
          <option value="">{t('common.none')}</option>
          {choices?.assignees.map((assignee: Choice) => (
            <option key={assignee.id} value={assignee.id}>
              {assignee.name}
            </option>
          ))}
        </select>
      </label>
      <label>
        {t('report.form-duplicate')}
        <input
          name="duplicateOfId"
          defaultValue={report.duplicateOfId ?? ''}
          disabled={!canDuplicate}
        />
      </label>
      {error && <p role="alert">{error}</p>}
      <button type="submit" disabled={pending}>
        {t('common.save')}
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
  if (!data) return <p role="status">{t('common.loading')}</p>

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
        <span className={`badge status-${report.status}`}>{statusLabel(report.status)}</span>{' '}
        <span className={`badge priority-${report.priority}`}>
          {priorityLabel(report.priority)}
        </span>
      </h1>
      <Media text={report.description} />
      <dl className="report-meta">
        <dt>{t('report.player')}</dt>
        <dd>
          {report.playerName} <span className="meta">{report.playerUuid}</span>
        </dd>
        <dt>{t('report.server')}</dt>
        <dd>
          {report.serverName} · {report.worldKey} · {report.x} {report.y} {report.z} ·{' '}
          {report.gameMode}
        </dd>
        <dt>{t('report.sent')}</dt>
        <dd>
          {formatDate(report.reportedAt)} · Paper {report.paperVersion}
        </dd>
        <dt>{t('report.assignee')}</dt>
        <dd>{report.assigneeName ?? t('common.none')}</dd>
        {report.duplicateOfId && (
          <>
            <dt>{t('report.duplicate')}</dt>
            <dd>
              <a href={`/reports/${report.duplicateOfId}`}>{report.duplicateOfId}</a>
            </dd>
          </>
        )}
        <dt>{t('report.participants')}</dt>
        <dd>
          {participants.length > 0
            ? participants.map((participant) => participant.name).join(', ')
            : t('common.none')}{' '}
          {can(me, 'reports.participate') && (
            <button type="button" onClick={toggleParticipation} disabled={pending}>
              {joined ? t('report.leave') : t('report.join')}
            </button>
          )}
        </dd>
      </dl>
      {(can(me, 'reports.status.update') ||
        can(me, 'reports.priority.update') ||
        can(me, 'reports.assignee.update') ||
        can(me, 'reports.duplicate.update')) && (
        <WorkflowForm report={report} onSaved={load} />
      )}
      {can(me, 'reports.inventory.view') && <Inventory reportId={report.id} />}
      <section aria-label={t('report.history')}>
        <h2>{t('report.history')}</h2>
        <ul className="audit">
          {events.map((event) => (
            <li key={event.id}>
              <span className="meta">{formatDate(event.createdAt)}</span>{' '}
              {eventLabel(event.eventType)} — {event.actorName ?? t('report.system')}
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
