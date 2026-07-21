import { useEffect, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api, can } from './api'
import { useAuth } from './auth'
import { formatDate, priorityLabel, statusLabel, t } from './i18n'
import {
  FILTER_KEYS,
  PRIORITIES,
  PRIORITY_ICONS,
  STATUSES,
  type Choices,
  type ReportSummary,
} from './report-model'

const MC = '/assets/mc'

export function FilterBar({ withStatus = false }: { withStatus?: boolean }) {
  const [params, setParams] = useSearchParams()
  const [choices, setChoices] = useState<Choices | null>(null)

  useEffect(() => {
    // ponytail: filters degrade to text-only if choices fail; page itself still works
    api<Choices>('/api/choices').then(setChoices, () => setChoices(null))
  }, [])

  function apply(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const next = new URLSearchParams()
    for (const key of FILTER_KEYS) {
      const value = String(data.get(key) ?? '').trim()
      if (value) next.set(key, value)
    }
    setParams(next, { replace: true })
  }

  return (
    <form className="filters" key={params.toString()} onSubmit={apply}>
      <input
        name="search"
        className="mc-input"
        aria-label={t('filters.search')}
        placeholder={t('filters.search-placeholder')}
        defaultValue={params.get('search') ?? ''}
      />
      <input
        name="category"
        className="mc-input"
        aria-label={t('filters.category')}
        placeholder={t('filters.category')}
        defaultValue={params.get('category') ?? ''}
      />
      <select name="serverId" className="mc-input" aria-label={t('filters.server')} defaultValue={params.get('serverId') ?? ''}>
        <option value="">{t('filters.all-servers')}</option>
        {choices?.servers.map((server) => (
          <option key={server.id} value={server.id}>
            {server.name}
          </option>
        ))}
      </select>
      <select name="priority" className="mc-input" aria-label={t('filters.priority')} defaultValue={params.get('priority') ?? ''}>
        <option value="">{t('filters.any-priority')}</option>
        {PRIORITIES.map((value) => (
          <option key={value} value={value}>
            {priorityLabel(value)}
          </option>
        ))}
      </select>
      <select name="assigneeId" className="mc-input" aria-label={t('filters.assignee')} defaultValue={params.get('assigneeId') ?? ''}>
        <option value="">{t('filters.any-assignee')}</option>
        {choices?.assignees.map((assignee) => (
          <option key={assignee.id} value={assignee.id}>
            {assignee.name}
          </option>
        ))}
      </select>
      {withStatus && (
        <select name="status" className="mc-input" aria-label={t('filters.status')} defaultValue={params.get('status') ?? ''}>
          <option value="">{t('filters.any-status')}</option>
          {STATUSES.map((status) => (
            <option key={status} value={status}>
              {statusLabel(status)}
            </option>
          ))}
        </select>
      )}
      <button type="submit" className="mc-btn mc-btn--primary mc-btn--sm">{t('filters.apply')}</button>
      <button type="button" className="mc-btn mc-btn--sm" onClick={() => setParams({}, { replace: true })}>
        {t('filters.reset')}
      </button>
    </form>
  )
}

export function ReportCard({
  report,
  onChange,
}: {
  report: ReportSummary
  onChange: (updated: ReportSummary) => void
}) {
  const { me } = useAuth()
  const [pending, setPending] = useState(false)
  const joined = report.participants.some((participant) => participant.name === me.username)

  async function toggleParticipation() {
    // ponytail: optimistic entry keyed by name; server list replaces it on next load
    const optimistic = joined
      ? report.participants.filter((participant) => participant.name !== me.username)
      : [...report.participants, { id: report.id, name: me.username ?? '' }]
    setPending(true)
    onChange({ ...report, participants: optimistic })
    try {
      await api(`/api/reports/${report.id}/participants`, {
        method: joined ? 'DELETE' : 'POST',
      })
    } catch (cause) {
      onChange(report)
      window.alert((cause as Error).message)
    } finally {
      setPending(false)
    }
  }

  return (
    <article className={`card priority-${report.priority}${report.priority === 'CRITICAL' ? ' mc-glint' : ''}`}>
      <header>
        <Link className="card-cat" to={`/reports/${report.id}`}>
          <img className="mc-ico mc-ico--sm" src={`${MC}/item/book.png`} alt="" />
          {report.category}
        </Link>
        <span className={`mc-chip priority-${report.priority}`}>
          <img className="mc-ico mc-ico--sm" src={`${MC}/${PRIORITY_ICONS[report.priority]}`} alt="" />
          {priorityLabel(report.priority)}
        </span>
      </header>
      <p className="snippet">{report.descriptionSnippet}</p>
      <p className="meta">
        <span>
          <img className="mc-ico mc-ico--sm" src={`${MC}/item/name_tag.png`} alt="" /> {report.playerName}
        </span>
        <span>
          <img className="mc-ico mc-ico--sm" src={`${MC}/block/beacon.png`} alt="" /> {report.serverName}
        </span>
        <span>
          <img className="mc-ico mc-ico--sm" src={`${MC}/item/clock_00.png`} alt="" />{' '}
          {formatDate(report.createdAt, 'short')}
        </span>
      </p>
      {report.assigneeName && <p className="meta">{t('report.assignee')}: {report.assigneeName}</p>}
      {report.participants.length > 0 && (
        <p className="meta">
          {t('report.participants')}: {report.participants.map((participant) => participant.name).join(', ')}
        </p>
      )}
      {can(me, 'reports.participate') && (
        <div className="card-actions">
          <button
            type="button"
            className={`mc-btn mc-btn--sm${joined ? '' : ' mc-btn--emerald'}`}
            onClick={toggleParticipation}
            disabled={pending}
          >
            {joined ? t('report.leave') : t('report.join')}
          </button>
        </div>
      )}
    </article>
  )
}
