import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { DragDropContext, Draggable, Droppable, type DropResult } from '@hello-pangea/dnd'
import { api } from './api'
import { useAuth } from './auth'
import {
  FilterBar,
  filterQuery,
  ReportCard,
  STATUS_LABELS,
  STATUSES,
  type Page,
  type Priority,
  type ReportSummary,
  type Status,
} from './reports'

// ponytail: 100 per column per plan; in-column pagination only when this ceiling hurts
const COLUMN_SIZE = 100

type Column = { reports: ReportSummary[]; total: number }
type Columns = Record<Status, Column>

export default function Board() {
  const { me } = useAuth()
  const [params] = useSearchParams()
  const [columns, setColumns] = useState<Columns | null>(null)
  const [error, setError] = useState<string | null>(null)
  const query = filterQuery(params)
  const admin = me.role === 'ADMIN'

  useEffect(() => {
    let cancelled = false
    setColumns(null)
    setError(null)
    Promise.all(
      STATUSES.map((status) =>
        api<Page>(`/api/reports?status=${status}&size=${COLUMN_SIZE}&${query}`),
      ),
    ).then(
      (pages) => {
        if (cancelled) return
        setColumns(
          Object.fromEntries(
            STATUSES.map((status, index) => [
              status,
              { reports: pages[index].reports, total: pages[index].total },
            ]),
          ) as Columns,
        )
      },
      (cause: Error) => {
        if (!cancelled) setError(cause.message)
      },
    )
    return () => {
      cancelled = true
    }
  }, [query])

  function replaceReport(updated: ReportSummary) {
    setColumns(
      (current) =>
        current &&
        (Object.fromEntries(
          Object.entries(current).map(([status, column]) => [
            status,
            {
              ...column,
              reports: column.reports.map((report) =>
                report.id === updated.id ? updated : report,
              ),
            },
          ]),
        ) as Columns),
    )
  }

  async function onDragEnd({ draggableId, source, destination }: DropResult) {
    if (!columns || !destination || destination.droppableId === source.droppableId) return
    const from = source.droppableId as Status
    const to = destination.droppableId as Status
    const report = columns[from].reports.find((candidate) => candidate.id === draggableId)
    if (!report) return

    let duplicateOfId: string | null = null
    if (to === 'DUPLICATE') {
      duplicateOfId = window.prompt('UUID оригинальной заявки:')?.trim() || null
      if (!duplicateOfId) return
    }

    const previous = columns
    const fromReports = previous[from].reports.filter((candidate) => candidate.id !== report.id)
    const toReports = [...previous[to].reports]
    toReports.splice(destination.index, 0, { ...report, status: to })
    setColumns({
      ...previous,
      [from]: { reports: fromReports, total: previous[from].total - 1 },
      [to]: { reports: toReports, total: previous[to].total + 1 },
    })

    try {
      // summary carries no assigneeId, so fetch detail for the full workflow PUT
      const { report: detail } = await api<{
        report: { priority: Priority; assigneeId: string | null }
      }>(`/api/reports/${report.id}`)
      await api(`/api/reports/${report.id}`, {
        method: 'PUT',
        body: JSON.stringify({
          status: to,
          priority: detail.priority,
          assigneeId: detail.assigneeId,
          duplicateOfId,
        }),
      })
    } catch (cause) {
      setColumns(previous)
      window.alert((cause as Error).message)
    }
  }

  return (
    <>
      <h1>Доска</h1>
      <FilterBar />
      {error && <p role="alert">{error}</p>}
      {!columns && !error && <p role="status">Загрузка…</p>}
      {columns && (
        <DragDropContext onDragEnd={onDragEnd}>
          <div className="board">
            {STATUSES.map((status) => {
              const column = columns[status]
              return (
                <section
                  key={status}
                  className={`column status-${status}`}
                  aria-label={STATUS_LABELS[status]}
                >
                  <h2>
                    {STATUS_LABELS[status]} <span className="meta">{column.total}</span>
                  </h2>
                  <Droppable droppableId={status}>
                    {(provided) => (
                      <div ref={provided.innerRef} {...provided.droppableProps} className="cards">
                        {column.reports.map((report, index) => (
                          <Draggable
                            key={report.id}
                            draggableId={report.id}
                            index={index}
                            isDragDisabled={!admin}
                          >
                            {(dragProvided) => (
                              <div
                                ref={dragProvided.innerRef}
                                {...dragProvided.draggableProps}
                                {...dragProvided.dragHandleProps}
                              >
                                <ReportCard report={report} onChange={replaceReport} />
                              </div>
                            )}
                          </Draggable>
                        ))}
                        {provided.placeholder}
                        {column.total > column.reports.length && (
                          <p className="meta">+{column.total - column.reports.length} ещё</p>
                        )}
                      </div>
                    )}
                  </Droppable>
                </section>
              )
            })}
          </div>
        </DragDropContext>
      )}
    </>
  )
}
