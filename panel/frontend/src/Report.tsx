import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import { useParams } from 'react-router-dom'
import { api, can } from './api'
import { useAuth } from './auth'
import { eventLabel, formatDate, priorityLabel, statusLabel, t } from './i18n'
import Inventory from './Inventory'
import { classifyUrls } from './media.mjs'
import {
  PRIORITIES,
  PRIORITY_ICONS,
  STATUS_ICONS,
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
    <form className="workflow mc-frame mc-frame--amethyst" onSubmit={submit} key={report.updatedAt}>
      <h2>
        <img className="mc-ico" src="/assets/mc/item/enchanted_book.png" alt="" />
        {t('report.form-title')}
      </h2>
      <div className="wf-row">
        <label>
          {t('report.form-status')}
          <select
            name="status"
            className="mc-input"
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
            className="mc-input"
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
      </div>
      <div className="wf-row">
        <label>
          {t('report.form-assignee')}
          <select
            name="assigneeId"
            className="mc-input"
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
            className="mc-input"
            defaultValue={report.duplicateOfId ?? ''}
            disabled={!canDuplicate}
          />
        </label>
      </div>
      {error && <p role="alert">{error}</p>}
      <div>
        <button type="submit" className="mc-btn mc-btn--gold" disabled={pending}>
          {t('common.save')}
        </button>
      </div>
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
      <header className="report-head">
        <img className="mc-ico mc-ico--lg" src="/assets/mc/item/book.png" alt="" />
        <h1>{report.category}</h1>
        <span className={`mc-chip status-${report.status}`}>
          <img className="mc-ico mc-ico--sm" src={`/assets/mc/${STATUS_ICONS[report.status]}`} alt="" />
          {statusLabel(report.status)}
        </span>
        <span className={`mc-chip priority-${report.priority}${report.priority === 'CRITICAL' ? ' mc-glint' : ''}`}>
          <img className="mc-ico mc-ico--sm" src={`/assets/mc/${PRIORITY_ICONS[report.priority]}`} alt="" />
          {priorityLabel(report.priority)}
        </span>
      </header>
      <div className="report-layout">
        <div className="report-main">
          <section className="report-section mc-panel" aria-label={t('report.description')}>
            <Media text={report.description} />
          </section>
          {can(me, 'reports.inventory.view') && <Inventory reportId={report.id} />}
          <section className="report-section mc-panel" aria-label={t('report.history')}>
            <h2>
              <img className="mc-ico" src="/assets/mc/item/clock_00.png" alt="" />
              {t('report.history')}
            </h2>
            <ul className="audit">
              {events.map((event) => (
                <li key={event.id}>
                  <span className="meta">{formatDate(event.createdAt)}</span>{' '}
                  {eventLabel(event.eventType)} — {event.actorName ?? t('report.system')}
                  {event.newValue && (
                    <span className="meta audit-diff">
                      {event.oldValue ? `${event.oldValue} → ` : ''}
                      {event.newValue}
                    </span>
                  )}
                </li>
              ))}
            </ul>
          </section>
        </div>
        <aside className="report-aside">
          <section className="report-section mc-panel" aria-label={t('report.meta')}>
            <dl className="report-meta">
              <div>
                <dt>
                  <img className="mc-ico mc-ico--sm" src="/assets/mc/item/name_tag.png" alt="" />{' '}
                  {t('report.player')}
                </dt>
                <dd>
                  {report.playerName} <span className="meta">{report.playerUuid}</span>
                </dd>
              </div>
              <div>
                <dt>
                  <img className="mc-ico mc-ico--sm" src="/assets/mc/block/beacon.png" alt="" />{' '}
                  {t('report.server')}
                </dt>
                <dd>
                  {report.serverName} · {report.worldKey} · {report.x} {report.y} {report.z} ·{' '}
                  {report.gameMode}
                </dd>
              </div>
              <div>
                <dt>
                  <img className="mc-ico mc-ico--sm" src="/assets/mc/item/clock_00.png" alt="" />{' '}
                  {t('report.sent')}
                </dt>
                <dd>
                  {formatDate(report.reportedAt)} · Paper {report.paperVersion}
                </dd>
              </div>
              <div>
                <dt>
                  <img className="mc-ico mc-ico--sm" src="/assets/mc/item/gold_ingot.png" alt="" />{' '}
                  {t('report.assignee')}
                </dt>
                <dd>{report.assigneeName ?? t('common.none')}</dd>
              </div>
              {report.duplicateOfId && (
                <div>
                  <dt>{t('report.duplicate')}</dt>
                  <dd>
                    <a href={`/reports/${report.duplicateOfId}`}>{report.duplicateOfId}</a>
                  </dd>
                </div>
              )}
              <div>
                <dt>
                  <img className="mc-ico mc-ico--sm" src="/assets/mc/item/armor_stand.png" alt="" />{' '}
                  {t('report.participants')}
                </dt>
                <dd>
                  {participants.length > 0
                    ? participants.map((participant) => participant.name).join(', ')
                    : t('common.none')}{' '}
                  {can(me, 'reports.participate') && (
                    <button
                      type="button"
                      className={`mc-btn mc-btn--sm${joined ? '' : ' mc-btn--emerald'}`}
                      onClick={toggleParticipation}
                      disabled={pending}
                    >
                      {joined ? t('report.leave') : t('report.join')}
                    </button>
                  )}
                </dd>
              </div>
            </dl>
          </section>
          {(can(me, 'reports.status.update') ||
            can(me, 'reports.priority.update') ||
            can(me, 'reports.assignee.update') ||
            can(me, 'reports.duplicate.update')) && (
            <WorkflowForm report={report} onSaved={load} />
          )}
        </aside>
      </div>
    </article>
  )
}
