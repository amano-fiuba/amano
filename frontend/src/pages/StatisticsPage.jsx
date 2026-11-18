import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { getStatisticsSummary, STATISTICS_ERROR_MESSAGE } from '../api/statisticsClient'
import { useAuth } from '../auth/AuthContext'
import HomeLink from '../components/HomeLink'
import Carousel from '../components/Carousel'
import PieChart from '../components/charts/PieChart'
import {
  CATEGORY_OPTIONS,
  INCOME_CATEGORY_OPTIONS,
  categoryColor,
  currencyLabel,
  incomeCategoryColor,
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

/**
 * Pie and category list of one kind of movement (FR-014, FR-015); the catalog and
 * the colors are parameters so expenses and incomes share it (plan D16).
 */
function Breakdown({ title, titleId, breakdown, currency, emptyMessage, options, colorOf }) {
  const items = breakdown.categories.map((entry) => {
    const name = labelOf(options, entry.category) || entry.category
    const amount = money(currency, entry.amount)
    const percentage = formatPercentage(entry.percentage)
    return {
      key: entry.category,
      label: name,
      color: colorOf(entry.category),
      value: Number(entry.amount),
      amount,
      percentage,
      detail: `${name} · ${amount} · ${percentage}`,
    }
  })
  return (
    <section className="statistics-breakdown" aria-labelledby={titleId}>
      <header className="statistics-breakdown__header">
        <h2 id={titleId}>{title}</h2>
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
 * replaces a newer one (plan D12, D18). The slide index belongs to the page so it
 * survives a period change (FR-005).
 */
function StatisticsResults({ period, onYears, slide, onSlideChange }) {
  const { setUser } = useAuth()
  const [attempt, setAttempt] = useState(0)
  const [state, setState] = useState({ attempt: -1, status: 'loading', data: null })

  useEffect(() => {
    let cancelled = false
    getStatisticsSummary(period).then((result) => {
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

  const { currency, expenses, income, comparison } = state.data
  const { personal, group } = expenses
  const name = periodName(period)
  const noExpenses = personal.categories.length === 0 && group.categories.length === 0
  const noIncome = income.categories.length === 0
  const notices = (
    <>
      {expenses.excludedCount > 0 && (
        <p className="statistics-excluded" role="status">
          {expenses.excludedCount === 1
            ? `1 gasto no se pudo convertir a ${currency} y no está incluido.`
            : `${expenses.excludedCount} gastos no se pudieron convertir a ${currency} y no están incluidos.`}
        </p>
      )}
      {income.excludedCount > 0 && (
        <p className="statistics-excluded" role="status">
          {income.excludedCount === 1
            ? `1 ingreso no se pudo convertir a ${currency} y no está incluido.`
            : `${income.excludedCount} ingresos no se pudieron convertir a ${currency} y no están incluidos.`}
        </p>
      )}
    </>
  )

  if (noExpenses && noIncome) {
    return (
      <>
        {notices}
        <p role="status">
          No registraste gastos ni ingresos en {name}.{' '}
          <Link to="/expenses/new">Registrar un gasto</Link>{' '}
          <Link to="/incomes/new">Registrar un ingreso</Link>
        </p>
      </>
    )
  }

  const balanceNumber = Number(comparison.balance)
  const balanceSign = balanceNumber < 0 ? '−' : balanceNumber > 0 ? '+' : ''
  const balanceText = `${balanceSign}${money(currency, comparison.balance.replace('-', ''))}`
  const comparisonItems = [
    {
      key: 'income',
      label: 'Ingresos',
      color: 'var(--color-ingreso-salary)',
      value: Number(comparison.income),
      amount: money(currency, comparison.income),
    },
    {
      key: 'expenses',
      label: 'Gastos',
      color: 'var(--color-categoria-shopping)',
      value: Number(comparison.expenses),
      amount: money(currency, comparison.expenses),
    },
  ]
  const totalValue = comparisonItems.reduce((sum, item) => sum + item.value, 0)
  for (const item of comparisonItems) {
    item.percentage = totalValue > 0 ? formatPercentage(((item.value / totalValue) * 100).toFixed(1)) : ''
    item.detail = `${item.label} · ${item.amount} · ${item.percentage}`
  }
  const chartItems = comparisonItems.filter((item) => item.value > 0)

  const expenseBreakdown = (title, id, breakdown, emptyMessage) => (
    <Breakdown
      title={title}
      titleId={id}
      breakdown={breakdown}
      currency={currency}
      emptyMessage={emptyMessage}
      options={CATEGORY_OPTIONS}
      colorOf={categoryColor}
    />
  )
  const slides = [
    {
      key: 'personal',
      label: 'Gastos personales',
      content: expenseBreakdown(
        'Gastos personales',
        'statistics-personal',
        personal,
        `No registraste gastos personales en ${name}.`,
      ),
    },
    {
      key: 'group',
      label: 'Gastos grupales',
      content: expenseBreakdown(
        'Gastos grupales',
        'statistics-group',
        group,
        `No tuviste gastos grupales en ${name}.`,
      ),
    },
  ]

  return (
    <>
      {notices}
      <section className="statistics-comparison" aria-labelledby="statistics-comparison">
        <h2 id="statistics-comparison">Ingresos y gastos de {name}</h2>
        <div className="statistics-comparison__body">
          {chartItems.length > 0 && (
            <PieChart
              items={chartItems}
              label={`Ingresos contra gastos: ${chartItems.map((item) => item.detail).join('; ')}`}
            />
          )}
          <ul className="statistics-legend" aria-label="Ingresos y gastos del período">
            {comparisonItems.map((item) => (
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
        <div className="statistics-comparison__summary">
          <p className={`statistics-balance${balanceNumber < 0 ? ' is-negative' : ''}`}>
            <span>Balance del período</span>
            <strong>{balanceText}</strong>
            {balanceNumber < 0 && <small>Los gastos superan a los ingresos</small>}
          </p>
          <small>
            Importes en {currencyLabel(currency)} ({currency})
          </small>
        </div>
      </section>
      {noExpenses ? (
        <p className="statistics-breakdown__empty" role="status">
          No registraste gastos en {name}. <Link to="/expenses/new">Registrar un gasto</Link>
        </p>
      ) : (
        <Carousel
          slides={slides}
          index={slide}
          onIndexChange={onSlideChange}
          ariaLabel="Gastos del período"
        />
      )}
      {noIncome ? (
        <section className="statistics-breakdown">
          <h2>Ingresos personales</h2>
          <p className="statistics-breakdown__empty" role="status">
            No registraste ingresos efectivizados en {name}.{' '}
            <Link to="/incomes/new">Registrar un ingreso</Link>
          </p>
        </section>
      ) : (
        <Breakdown
          title="Ingresos personales"
          titleId="statistics-income"
          breakdown={income}
          currency={currency}
          emptyMessage=""
          options={INCOME_CATEGORY_OPTIONS}
          colorOf={incomeCategoryColor}
        />
      )}
    </>
  )
}

// Pantalla de estadísticas (032 y 034): comparación de ingresos y gastos, carrusel
// de gastos personales y grupales e ingresos personales por categoría, de a un mes. El período vive en la dirección, reemplazando la
// entrada del historial para no llenar "Atrás".
export default function StatisticsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const period = periodFromParams(searchParams)
  const [years, setYears] = useState([])
  const [slide, setSlide] = useState(0)

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
        slide={slide}
        onSlideChange={setSlide}
      />
    </main>
  )
}
