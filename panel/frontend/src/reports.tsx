import { useEffect, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api, can } from './api'
import { useAuth } from './auth'

export type Status = 'NEW' | 'IN_PROGRESS' | 'RESOLVED' | 'REJECTED' | 'DUPLICATE'
export type Priority = 'LOW' | 'NORMAL' | 'HIGH' | 'CRITICAL'
export type Participant = { id: string; name: string }

export type ReportSummary = {
  id: string
  serverName: string
  category: string
  playerName: string
  descriptionSnippet: string
  status: Status
  priority: Priority
  assigneeName: string | null
  participants: Participant[]
  hasInventory: boolean
  createdAt: string
}

export type Page = { reports: ReportSummary[]; number: number; size: number; total: number }
export type Choice = { id: string; name: string }
export type Choices = { servers: Choice[]; assignees: Choice[] }

export const STATUSES: Status[] = ['NEW', 'IN_PROGRESS', 'RESOLVED', 'REJECTED', 'DUPLICATE']

export const STATUS_LABELS: Record<Status, string> = {
  NEW: 'Новые',
  IN_PROGRESS: 'В работе',
  RESOLVED: 'Решены',
  REJECTED: 'Отклонены',
  DUPLICATE: 'Дубликаты',
}

export const PRIORITY_LABELS: Record<Priority, string> = {
  LOW: 'Низкий',
  NORMAL: 'Обычный',
  HIGH: 'Высокий',
  CRITICAL: 'Критический',
}

const FILTER_KEYS = ['search', 'serverId', 'priority', 'assigneeId', 'category', 'status']

export function filterQuery(params: URLSearchParams): string {
  const query = new URLSearchParams()
  for (const key of FILTER_KEYS) {
    const value = params.get(key)
    if (value) query.set(key, value)
  }
  return query.toString()
}

const dateTimeFormat = new Intl.DateTimeFormat(undefined, {
  dateStyle: 'short',
  timeStyle: 'short',
})

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
        aria-label="Поиск"
        placeholder="Поиск…"
        defaultValue={params.get('search') ?? ''}
      />
      <input
        name="category"
        aria-label="Категория"
        placeholder="Категория"
        defaultValue={params.get('category') ?? ''}
      />
      <select name="serverId" aria-label="Сервер" defaultValue={params.get('serverId') ?? ''}>
        <option value="">Все серверы</option>
        {choices?.servers.map((server) => (
          <option key={server.id} value={server.id}>
            {server.name}
          </option>
        ))}
      </select>
      <select name="priority" aria-label="Приоритет" defaultValue={params.get('priority') ?? ''}>
        <option value="">Любой приоритет</option>
        {Object.entries(PRIORITY_LABELS).map(([value, label]) => (
          <option key={value} value={value}>
            {label}
          </option>
        ))}
      </select>
      <select name="assigneeId" aria-label="Ответственный" defaultValue={params.get('assigneeId') ?? ''}>
        <option value="">Любой ответственный</option>
        {choices?.assignees.map((assignee) => (
          <option key={assignee.id} value={assignee.id}>
            {assignee.name}
          </option>
        ))}
      </select>
      {withStatus && (
        <select name="status" aria-label="Статус" defaultValue={params.get('status') ?? ''}>
          <option value="">Любой статус</option>
          {STATUSES.map((status) => (
            <option key={status} value={status}>
              {STATUS_LABELS[status]}
            </option>
          ))}
        </select>
      )}
      <button type="submit">Применить</button>
      <button type="button" onClick={() => setParams({}, { replace: true })}>
        Сбросить
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
    <article className="card">
      <header>
        <Link to={`/reports/${report.id}`}>{report.category}</Link>
        <span className={`badge priority-${report.priority}`}>
          {PRIORITY_LABELS[report.priority]}
        </span>
      </header>
      <p className="snippet">{report.descriptionSnippet}</p>
      <p className="meta">
        {report.playerName} · {report.serverName} ·{' '}
        {dateTimeFormat.format(new Date(report.createdAt))}
      </p>
      {report.assigneeName && <p className="meta">Ответственный: {report.assigneeName}</p>}
      {report.participants.length > 0 && (
        <p className="meta">
          Участники: {report.participants.map((participant) => participant.name).join(', ')}
        </p>
      )}
      {can(me, 'reports.participate') && (
        <button type="button" onClick={toggleParticipation} disabled={pending}>
          {joined ? 'Покинуть' : 'Присоединиться'}
        </button>
      )}
    </article>
  )
}
