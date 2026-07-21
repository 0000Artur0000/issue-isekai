import { Fragment, useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { api } from './api'
import { formatDate, t } from './i18n'
import { filterQuery, type Page, type ReportSummary } from './report-model'
import { FilterBar, ReportCard } from './reports'

const PAGE_SIZE = 20
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
      <div className="page-head">
        <img src="/assets/mc/item/clock_00.png" alt="" />
        <h1>{t('timeline.title')}</h1>
      </div>
      <FilterBar withStatus />
      {error && (
        <div className="state-error mc-panel" role="alert">
          <img src="/assets/mc/item/barrier.png" alt="" />
          {error}
        </div>
      )}
      {!loading && !error && reports.length === 0 && (
        <div className="state-empty mc-panel">
          <img src="/assets/mc/big/chest_minecart.png" alt="" />
          {t('timeline.empty')}
        </div>
      )}
      <div className="timeline">
        {reports.map((report) => {
          const day = formatDate(report.createdAt, 'long')
          const heading =
            day !== previousDay ? (
              <h2>
                <img src="/assets/mc/item/amethyst_shard.png" alt="" />
                {day}
              </h2>
            ) : null
          previousDay = day
          return (
            <Fragment key={report.id}>
              {heading}
              <ReportCard report={report} onChange={replaceReport} />
            </Fragment>
          )
        })}
      </div>
      {loading && (
        <p role="status" className="state-loading">
          {t('common.loading')}
        </p>
      )}
      {!loading && reports.length < total && (
        <button type="button" className="mc-btn mc-btn--primary" onClick={() => setPage(page + 1)}>
          {t('timeline.more', { count: total - reports.length })}
        </button>
      )}
    </>
  )
}
