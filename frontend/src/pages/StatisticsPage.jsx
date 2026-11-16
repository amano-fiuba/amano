import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { getExpenseStatistics, STATISTICS_ERROR_MESSAGE } from '../api/expenseClient'
import { useAuth } from '../auth/AuthContext'
import HomeLink from '../components/HomeLink'
import PieChart from '../components/charts/PieChart'
import {
  CATEGORY_OPTIONS,
  categoryColor,
  currencyLabel,
  labelOf,
} from '../validation/expenseCatalog'
import { formatAmount } from '../validation/expenseFormat'
import { MONTH_NAMES } from '../validation/months'
import './MainPage.scss'
import './groups/groups.scss'
import './StatisticsPage.scss'

function today() {
  const now = new Date()
  return { year: String(now.getFullYear()), month: String(now.getMonth() + 1) }
}

/** Period in the address (`?year=&month=`, plan D11); a missing or invalid value is the current one. */
function periodFromParams(params) {
  const current = today()
  const rawYear = params.get('year') ?? ''
  const rawMonth = params.get('month') ?? ''
  return {
    year: /^\d{4}$/.test(rawYear) ? rawYear : current.year,
    month: /^(1[0-2]|[1-9])$/.test(rawMonth) ? rawMonth : current.month,
  }
}

/** Address of a period: the current year and month are left out. */
function paramsOf({ year, month }) {
  const current = today()
  const params = {}
  if (year !== current.year) {
    params.year = year
  }
  if (month !== current.month) {
    params.month = month
  }
  return params
}

/** Current year, years with expenses and the one in the address, newest first. */
function yearOptionsOf(years, selected) {
  const set = new Set([today().year, selected, ...years.map(String)])
  return [...set].sort((a, b) => Number(b) - Number(a))
}

function periodName({ year, month }) {
  return `${MONTH_NAMES[Number(month) - 1].toLowerCase()} de ${year}`
}

function formatPercentage(value) {
  return `${String(value).replace('.', ',')} %`
}

function money(currency, amount) {
  return `${currency} ${formatAmount(amount)}`
}

/** Pie and category list of one kind of expense (FR-014, FR-015). */
function Breakdown({ title, breakdown, currency, emptyMessage }) {
  const items = breakdown.categories.map((entry) => {
    const name = labelOf(CATEGORY_OPTIONS, entry.category) || entry.category
    const amount = money(currency, entry.amount)
    const percentage = formatPercentage(entry.percentage)
    return {
      key: entry.category,
      label: name,
      color: categoryColor(entry.category),
      value: Number(entry.amount),
      amount,
      percentage,
      detail: `${name} · ${amount} · ${percentage}`,
    }
  })
  return (
    <section className="statistics-breakdown" aria-labelledby={`statistics-${title}`}>
      <header className="statistics-breakdown__header">
        <h2 id={`statistics-${title}`}>{title}</h2>
        {items.length > 0 && <strong>{money(currency, breakdown.total)}</strong>}
      </header>
      {items.length === 0 ? (
        <p className="statistics-breakdown__empty">{emptyMessage}</p>
      ) : (
        <div className="statistics-breakdown__body">
          <PieChart
            items={items}
            label={`${title}: ${items.map((item) => item.detail).join('; ')}`}
          />
          <ul className="statistics-legend" aria-label={`Categorías de ${title.toLowerCase()}`}>
            {items.map((item) => (
              <li key={item.key}>
                <span
                  className="statistics-legend__swatch"
                  style={{ background: item.color }}
                  aria-hidden="true"
                />
                <span className="statistics-legend__name">{item.label}</span>
                <span className="statistics-legend__amount">{item.amount}</span>
                <span className="statistics-legend__percentage">{item.percentage}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </section>
  )
}

/**
 * Statistics of one period, keyed by it so an answer for a previous period never
 * replaces a newer one (plan D12).
 */
function StatisticsResults({ period, onYears }) {
  const { setUser } = useAuth()
  const [attempt, setAttempt] = useState(0)
  const [state, setState] = useState({ attempt: -1, status: 'loading', data: null })

  useEffect(() => {
    let cancelled = false
    getExpenseStatistics(period).then((result) => {
      if (cancelled) {
        return
      }
      if (result.ok) {
        setState({ attempt, status: 'ready', data: result.statistics })
        onYears(result.statistics.years)
      } else if (result.status === 401) {
        setUser(null)
      } else {
        setState({ attempt, status: 'error', data: null })
      }
    })
    return () => {
      cancelled = true
    }
    // The period is fixed for this component (it is keyed by it).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [attempt, setUser])

  if (state.attempt !== attempt) {
    return <p aria-busy="true">Cargando…</p>
  }
  if (state.status === 'error') {
    return (
      <div className="activity-search__error" role="alert">
        <p>{STATISTICS_ERROR_MESSAGE}</p>
        <button type="button" onClick={() => setAttempt((value) => value + 1)}>
          Reintentar
        </button>
      </div>
    )
  }

  const { currency, total, excludedCount, personal, group } = state.data
  const name = periodName(period)
  const excluded =
    excludedCount > 0 && (
      <p className="statistics-excluded" role="status">
        {excludedCount === 1
          ? `1 gasto no se pudo convertir a ${currency} y no está incluido.`
          : `${excludedCount} gastos no se pudieron convertir a ${currency} y no están incluidos.`}
      </p>
    )

  if (personal.categories.length === 0 && group.categories.length === 0) {
    return (
      <>
        {excluded}
        <p role="status">
          No registraste gastos en {name}. <Link to="/expenses/new">Registrar un gasto</Link>
        </p>
      </>
    )
  }
  return (
    <>
      <section className="statistics-total" aria-label="Total del mes">
        <span>Total de {name}</span>
        <strong>{money(currency, total)}</strong>
        <small>
          Importes en {currencyLabel(currency)} ({currency})
        </small>
      </section>
      {excluded}
      <div className="statistics-breakdowns">
        <Breakdown
          title="Personales"
          breakdown={personal}
          currency={currency}
          emptyMessage={`No registraste gastos personales en ${name}.`}
        />
        <Breakdown
          title="Grupales"
          breakdown={group}
          currency={currency}
          emptyMessage={`No tuviste gastos grupales en ${name}.`}
        />
      </div>
    </>
  )
}

// Pantalla de estadísticas (specs/032-estadisticas-gastos): distribución por
// categoría de los gastos personales y de la parte de la persona en los gastos
// grupales, de a un mes. El período vive en la dirección, reemplazando la
// entrada del historial para no llenar "Atrás".
export default function StatisticsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const period = periodFromParams(searchParams)
  const [years, setYears] = useState([])

  const navigate = (next) => setSearchParams(paramsOf(next), { replace: true })

  return (
    <main className="page statistics-page">
      <HomeLink />
      <h1>Estadísticas</h1>

      <form className="statistics-filters" onSubmit={(event) => event.preventDefault()}>
        <select
          className="list-search__sort"
          aria-label="Mes"
          value={period.month}
          onChange={(event) => navigate({ ...period, month: event.target.value })}
        >
          {MONTH_NAMES.map((name, index) => (
            <option key={name} value={String(index + 1)}>
              {name}
            </option>
          ))}
        </select>
        <select
          className="list-search__sort"
          aria-label="Año"
          value={period.year}
          onChange={(event) => navigate({ ...period, year: event.target.value })}
        >
          {yearOptionsOf(years, period.year).map((option) => (
            <option key={option} value={option}>
              {option}
            </option>
          ))}
        </select>
      </form>

      <StatisticsResults
        key={`${period.year}-${period.month}`}
        period={period}
        onYears={setYears}
      />
    </main>
  )
}
