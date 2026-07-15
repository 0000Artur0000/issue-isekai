import { Fragment, useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { api } from './api'
import { FilterBar, filterQuery, ReportCard, type Page, type ReportSummary } from './reports'

const PAGE_SIZE = 20
const dayFormat = new Intl.DateTimeFormat(undefined, { dateStyle: 'long' })

export default function Timeline() {
  const [params] = useSearchParams()
  const [reports, setReports] = useState<ReportSummary[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const query = filterQuery(params)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    api<Page>(`/api/reports?page=${page}&size=${PAGE_SIZE}&${query}`).then(
      (result) => {
        if (cancelled) return
        setReports((current) => (page === 0 ? result.reports : [...current, ...result.reports]))
        setTotal(result.total)
        setLoading(false)
      },
      (cause: Error) => {
        if (cancelled) return
        setError(cause.message)
        setLoading(false)
      },
    )
    return () => {
      cancelled = true
    }
  }, [query, page])

  // reset to first page when filters change
  useEffect(() => {
    setPage(0)
    setReports([])
  }, [query])

  function replaceReport(updated: ReportSummary) {
    setReports((current) =>
      current.map((report) => (report.id === updated.id ? updated : report)),
    )
  }

  let previousDay = ''
  return (
    <>
      <h1>Лента</h1>
      <FilterBar withStatus />
      {error && <p role="alert">{error}</p>}
      {!loading && !error && reports.length === 0 && <p>Заявок нет.</p>}
      <div className="timeline">
        {reports.map((report) => {
          const day = dayFormat.format(new Date(report.createdAt))
          const heading = day !== previousDay ? <h2>{day}</h2> : null
          previousDay = day
          return (
            <Fragment key={report.id}>
              {heading}
              <ReportCard report={report} onChange={replaceReport} />
            </Fragment>
          )
        })}
      </div>
      {loading && <p role="status">Загрузка…</p>}
      {!loading && reports.length < total && (
        <button type="button" onClick={() => setPage(page + 1)}>
          Показать ещё ({total - reports.length})
        </button>
      )}
    </>
  )
}
