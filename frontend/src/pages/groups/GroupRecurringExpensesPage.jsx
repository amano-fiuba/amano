import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import {
  listGroupRecurringExpenses,
  updateGroupRecurringExpense,
} from '../../api/groupsClient'
import { useSnackbar } from '../../components/Snackbar'
import {
  CATEGORY_OPTIONS,
  FREQUENCY_OPTIONS,
  STATUS_OPTIONS,
  labelOf,
} from '../../validation/expenseCatalog'
import { formatAmount, formatIsoDate } from '../../validation/expenseFormat'
import './groups.scss'

export const EMPTY_MESSAGE = 'Todavía no hay gastos recurrentes en este grupo'
export const LOADING_MESSAGE = 'Cargando gastos recurrentes…'
export const LOAD_ERROR_MESSAGE = 'No se pudieron cargar los gastos recurrentes. Intentá nuevamente'
export const UPDATE_ERROR_MESSAGE = 'No se pudo actualizar el gasto recurrente. Intentá nuevamente'
export const PAUSED_MESSAGE = 'El gasto recurrente se pausó'
export const RESUMED_MESSAGE = 'El gasto recurrente se reactivó'
export const FREQUENCY_MESSAGE = 'Se actualizó la frecuencia del gasto recurrente'

function titleOf(series) {
  return series.description || labelOf(CATEGORY_OPTIONS, series.category) || 'Gasto recurrente'
}

/**
 * 028: recurring series of a group. Any member can see them; the creator, the
 * payer or an admin can pause/resume each series and change its frequency.
 * A frequency change on an active series recomputes the next date on the server.
 */
export default function GroupRecurringExpensesPage() {
  const params = useParams()
  const groupId = params.id
  const showSnackbar = useSnackbar()
  const [load, setLoad] = useState({ status: 'loading', series: [] })
  const [reloadToken, setReloadToken] = useState(0)
  const [pendingId, setPendingId] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    listGroupRecurringExpenses(groupId).then((result) => {
      if (cancelled) return
      if (result.ok) {
        setLoad({ status: 'ready', series: result.series })
      } else {
        setLoad({ status: 'error', series: [] })
      }
    })
    return () => {
      cancelled = true
    }
  }, [groupId, reloadToken])

  function retry() {
    setLoad({ status: 'loading', series: [] })
    setReloadToken((token) => token + 1)
  }

  async function change(series, payload, successMessage) {
    setPendingId(series.id)
    setError('')
    const result = await updateGroupRecurringExpense(groupId, series.id, payload)
    setPendingId(null)
    if (!result.ok) {
      setError(result.network ? UPDATE_ERROR_MESSAGE : result.detail || UPDATE_ERROR_MESSAGE)
      if (result.status !== 400) {
        setReloadToken((token) => token + 1)
      }
      return
    }
    setLoad((current) => ({
      ...current,
      series: current.series.map((item) => (item.id === result.series.id ? result.series : item)),
    }))
    showSnackbar(successMessage)
  }

  return (
    <main className="page recurring-page">
      <nav className="group-page__back">
        <Link to={`/groups/${groupId}`}>
          <span aria-hidden="true">←</span> Volver al grupo
        </Link>
      </nav>
      <h1>Gastos recurrentes</h1>

      {error && (
        <p role="alert" data-testid="group-recurring-error">
          {error}
        </p>
      )}

      {load.status === 'loading' && <p role="status">{LOADING_MESSAGE}</p>}

      {load.status === 'error' && (
        <div data-testid="group-recurring-load-error">
          <p role="alert">{LOAD_ERROR_MESSAGE}</p>
          <button type="button" className="buttonLink" onClick={retry}>
            Reintentar
          </button>
        </div>
      )}

      {load.status === 'ready' && load.series.length === 0 && (
        <p data-testid="group-recurring-empty">{EMPTY_MESSAGE}.</p>
      )}

      {load.status === 'ready' && load.series.length > 0 && (
        <ul className="recurring-list" aria-label="Gastos recurrentes del grupo">
          {load.series.map((series) => {
            const busy = pendingId === series.id
            const title = titleOf(series)
            return (
              <li key={series.id} className="recurring-item" data-testid={`group-recurring-${series.id}`}>
                <div className="recurring-item__header">
                  <strong>{title}</strong>
                  <span className={`recurring-status recurring-status--${series.status}`}>
                    {labelOf(STATUS_OPTIONS, series.status)}
                  </span>
                </div>
                <p>
                  {formatAmount(series.amount)} {series.currency} ·{' '}
                  {labelOf(CATEGORY_OPTIONS, series.category)}
                </p>
                <p className="recurring-item__dates">
                  Desde {formatIsoDate(series.date)}
                  {series.nextOccurrenceDate &&
                    ` · Próxima fecha: ${formatIsoDate(series.nextOccurrenceDate)}`}
                </p>

                <div className="recurring-item__actions">
                  <label htmlFor={`frequency-${series.id}`}>
                    Frecuencia
                  </label>
                  <select
                    id={`frequency-${series.id}`}
                    aria-label={`Frecuencia de ${title}`}
                    value={series.frequency}
                    disabled={busy}
                    onChange={(event) =>
                      change(series, { frequency: event.target.value }, FREQUENCY_MESSAGE)}
                  >
                    {FREQUENCY_OPTIONS.map((option) => (
                      <option key={option.code} value={option.code}>
                        {option.label}
                      </option>
                    ))}
                  </select>

                  <button
                    type="button"
                    className="buttonLink"
                    disabled={busy}
                    onClick={() => change(
                      series,
                      { status: series.status === 'ACTIVE' ? 'PAUSED' : 'ACTIVE' },
                      series.status === 'ACTIVE' ? PAUSED_MESSAGE : RESUMED_MESSAGE,
                    )}
                  >
                    {busy
                      ? 'Guardando…'
                      : series.status === 'ACTIVE' ? 'Pausar' : 'Reactivar'}
                  </button>
                </div>
              </li>
            )
          })}
        </ul>
      )}
    </main>
  )
}
