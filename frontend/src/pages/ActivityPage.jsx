import { useEffect, useEffectEvent, useLayoutEffect, useRef, useState } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router'
import {
  listExpenseYears,
  SEARCH_EXPENSES_ERROR_MESSAGE,
  searchExpenses,
} from '../api/expenseClient'
import { useAuth } from '../auth/AuthContext'
import { SearchIcon } from '../components/NavIcons'
import { CATEGORY_OPTIONS, PAYMENT_METHOD_OPTIONS, labelOf } from '../validation/expenseCatalog'
import { formatAmount, formatIsoDate } from '../validation/expenseFormat'
import './MainPage.scss'
import './groups/groups.scss'
import HomeLink from '../components/HomeLink'

// Exact messages of specs/031-buscar-en-actividad-personal, exported for the tests.
export const SEARCH_PLACEHOLDER = 'Buscar por categoría, descripción, medio de pago'
export const TOO_SHORT_MESSAGE = 'Ingresá al menos 2 caracteres para buscar'
export const NO_RESULTS_MESSAGE = 'No se encontraron resultados'

const MIN_LENGTH = 2
const MAX_LENGTH = 100
// Pause after the last keystroke before querying (FR-001): one query per pause.
export const DEBOUNCE_MS = 400
export const ALL_YEARS = 'all'
export const ALL_MONTHS = ''
const MONTHS = [
  'Enero',
  'Febrero',
  'Marzo',
  'Abril',
  'Mayo',
  'Junio',
  'Julio',
  'Agosto',
  'Septiembre',
  'Octubre',
  'Noviembre',
  'Diciembre',
]

// Page size from the screen height (FR-022, plan D13): the cards that fit below
// the filters without scrolling, between 5 and 20.
export const MIN_PAGE_SIZE = 5
export const MAX_PAGE_SIZE = 20
// Height of one card plus the gap until the first ones are measured.
const ESTIMATED_ROW_HEIGHT = 86
// Space kept below the list for the counter, the pagination and the page margin.
const RESERVED_HEIGHT = 120
const FILTERS_MARGIN = 16
const RESIZE_PAUSE_MS = 150
// With more pages than this, only the first, the last and the current with its
// neighbours are shown (FR-021).
const MAX_PLAIN_PAGES = 7

function currentYear() {
  return String(new Date().getFullYear())
}

function isSearchable(term) {
  return term.length >= MIN_LENGTH && term.length <= MAX_LENGTH
}

/**
 * Criteria in force, read from the address (`?q=&year=&month=&page=`, plan D7,
 * D12, D14). Without `year` the screen shows the current year; an invalid year
 * falls back to it, an invalid month to the whole year (also with "Todos los
 * años"), an invalid page to the first one and a term that is too short or too
 * long is ignored (edge cases).
 */
function criteriaFromParams(params) {
  const rawTerm = (params.get('q') ?? '').trim()
  const rawYear = params.get('year')
  const year = rawYear === ALL_YEARS || /^\d{4}$/.test(rawYear ?? '') ? rawYear : currentYear()
  const rawMonth = params.get('month') ?? ''
  const month = year !== ALL_YEARS && /^(1[0-2]|[1-9])$/.test(rawMonth) ? rawMonth : ALL_MONTHS
  const rawPage = params.get('page') ?? ''
  const page = /^\d{1,6}$/.test(rawPage) && Number(rawPage) >= 1 ? Number(rawPage) : 1
  return { term: isSearchable(rawTerm) ? rawTerm : '', year, month, page }
}

/** Address of some criteria: the defaults (no term, current year, all months, page 1) are left out. */
function paramsOf({ term, year, month, page }) {
  const params = {}
  if (term) {
    params.q = term
  }
  if (year !== currentYear()) {
    params.year = year
  }
  if (month) {
    params.month = month
  }
  if (page > 1) {
    params.page = String(page)
  }
  return params
}

/** Current year, years with expenses (newest first) and the one in the address. */
function yearOptionsOf(years, selected) {
  const set = new Set([currentYear(), ...years.map(String)])
  if (selected && selected !== ALL_YEARS) {
    set.add(selected)
  }
  return [...set].sort((a, b) => Number(b) - Number(a))
}

/** Cards that fit between the filters and the bottom of the window (FR-022). */
function pageSizeFor(filters, rowHeight) {
  const top = filters ? filters.getBoundingClientRect().bottom + window.scrollY : 0
  const available = window.innerHeight - top - FILTERS_MARGIN - RESERVED_HEIGHT
  const fitting = Math.floor(available / rowHeight)
  return Math.min(MAX_PAGE_SIZE, Math.max(MIN_PAGE_SIZE, fitting))
}

/** Height of one card plus the gap between cards, or null when not laid out. */
function rowHeightOf(list) {
  const count = list.children.length
  const height = list.getBoundingClientRect().height
  if (count === 0 || height <= 0) {
    return null
  }
  const gap = Number.parseFloat(window.getComputedStyle(list).rowGap) || 0
  return (height + gap) / count
}

/** Page numbers to show, with '…' in the jumps (FR-021). */
function pagesOf(current, total) {
  if (total <= MAX_PLAIN_PAGES) {
    return Array.from({ length: total }, (_, index) => index + 1)
  }
  const shown = [1, current - 1, current, current + 1, total]
    .filter((page, index, all) => page >= 1 && page <= total && all.indexOf(page) === index)
    .sort((a, b) => a - b)
  const items = []
  shown.forEach((page, index) => {
    if (index > 0 && page - shown[index - 1] > 1) {
      items.push(`gap-${page}`)
    }
    items.push(page)
  })
  return items
}

// Each expense is a card with a chevron, like the rows of "Mis grupos" (FR-013).
function ExpenseItem({ expense, from }) {
  const category = labelOf(CATEGORY_OPTIONS, expense.category)
  return (
    <li>
      <Link to={`/expenses/${expense.id}`} state={{ from }} className="group-row expense-row">
        <span className="group-row__body">
          <span className="group-row__title">
            <strong>{expense.description || category}</strong>
            <span className="main-expense__amount">
              {expense.currency} {formatAmount(expense.amount)}
              {Array.isArray(expense.conversions) &&
                expense.conversions.map((conversion) => (
                  <small key={conversion.currency} className="main-expense__conversion">
                    ≈ {conversion.currency} {formatAmount(conversion.amount)}
                  </small>
                ))}
            </span>
          </span>
          <small>
            {formatIsoDate(expense.date)} · {category} ·{' '}
            {labelOf(PAYMENT_METHOD_OPTIONS, expense.paymentMethod)}
          </small>
        </span>
        <span className="group-row__chevron" aria-hidden="true">
          ›
        </span>
      </Link>
    </li>
  )
}

function Pagination({ page, totalPages, onChange }) {
  return (
    <nav className="activity-pagination" aria-label="Paginación">
      <button type="button" disabled={page <= 1} onClick={() => onChange(page - 1)}>
        ‹ Anterior
      </button>
      <ol>
        {pagesOf(page, totalPages).map((item) =>
          typeof item === 'number' ? (
            <li key={item}>
              <button
                type="button"
                aria-label={`Página ${item}`}
                aria-current={item === page ? 'page' : undefined}
                onClick={() => onChange(item)}
              >
                {item}
              </button>
            </li>
          ) : (
            <li key={item} aria-hidden="true" className="activity-pagination__gap">
              …
            </li>
          ),
        )}
      </ol>
      <button type="button" disabled={page >= totalPages} onClick={() => onChange(page + 1)}>
        Siguiente ›
      </button>
    </nav>
  )
}

function emptyMessageOf(year, month) {
  if (year === ALL_YEARS) {
    return 'Todavía no registraste gastos.'
  }
  if (month) {
    return `No registraste gastos en ${MONTHS[Number(month) - 1].toLowerCase()} de ${year}.`
  }
  return `No registraste gastos en ${year}.`
}

/**
 * One page of the expenses of the criteria in force (FR-003..FR-009, FR-012,
 * FR-019, FR-021): the whole year/month (or every year) without a term, the
 * matches with one. Keyed by the filters, so an answer of a previous search never
 * overwrites a newer one (D9); while another page loads the previous one stays
 * visible, dimmed, so the pagination does not jump.
 */
function ActivityResults({ term, year, month, page, size, from, onPage, onPageResolved, onListShown }) {
  const { setUser } = useAuth()
  const [attempt, setAttempt] = useState(0)
  const [state, setState] = useState({ key: null, status: 'loading', data: null })
  const listRef = useRef(null)
  const requestKey = `${page}|${size}|${attempt}`

  useEffect(() => {
    let cancelled = false
    searchExpenses({
      term,
      year: year === ALL_YEARS ? null : year,
      month: month || null,
      page,
      size,
    }).then((result) => {
      if (cancelled) {
        return
      }
      if (result.ok) {
        setState({ key: requestKey, status: 'ready', data: result })
        if (result.page !== page) {
          // The asked page does not exist any more: the backend sent the last one.
          onPageResolved(result.page)
        }
      } else if (result.status === 401) {
        setUser(null)
      } else {
        setState({ key: requestKey, status: 'error', data: null })
      }
    })
    return () => {
      cancelled = true
    }
    // onPageResolved changes on every render of the page; the request only
    // depends on the criteria.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [term, year, month, page, size, attempt, setUser])

  const loading = state.key !== requestKey
  const data = state.data

  useEffect(() => {
    if (!loading && listRef.current) {
      onListShown(listRef.current)
    }
  }, [loading, data, onListShown])

  if (loading && !data) {
    return <p aria-busy="true">Buscando…</p>
  }
  if (!loading && state.status === 'error') {
    return (
      <div className="activity-search__error" role="alert">
        <p>{SEARCH_EXPENSES_ERROR_MESSAGE}</p>
        <button type="button" onClick={() => setAttempt((value) => value + 1)}>
          Reintentar
        </button>
      </div>
    )
  }
  if (data.expenses.length === 0) {
    if (term) {
      return (
        <p role="status" className="activity-search__empty">
          {NO_RESULTS_MESSAGE}
        </p>
      )
    }
    return (
      <p role="status">
        {emptyMessageOf(year, month)} <Link to="/expenses/new">Registrar un gasto</Link>
      </p>
    )
  }
  return (
    <div className="activity-results" aria-busy={loading}>
      {term && (
        <p className="activity-search__count" aria-live="polite">
          {data.totalElements === 1 ? '1 resultado' : `${data.totalElements} resultados`}
        </p>
      )}
      <ul ref={listRef} className="group-rows expense-rows" aria-label="Gastos">
        {data.expenses.map((expense) => (
          <ExpenseItem key={expense.id} expense={expense} from={from} />
        ))}
      </ul>
      {data.totalPages > 1 && (
        <Pagination page={data.page} totalPages={data.totalPages} onChange={onPage} />
      )}
    </div>
  )
}

// Pantalla de actividad personal (specs/031-buscar-en-actividad-personal): muestra
// lo que indican los selectores de mes y año (por defecto, todo el año actual) y,
// si hay texto, solo lo que coincide, de a una página cuyo tamaño depende del alto
// de la pantalla. Se consulta al dejar de escribir (o con Enter), al borrar el
// texto (la x del campo), al cambiar el mes, el año o la página. Los criterios
// viven en la dirección, reemplazando la entrada del historial para no llenar "Atrás".
export default function ActivityPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const location = useLocation()
  const criteria = criteriaFromParams(searchParams)
  const [term, setTerm] = useState(criteria.term)
  const [years, setYears] = useState([])
  const [pageSize, setPageSize] = useState(null)
  const topRef = useRef(null)
  const filtersRef = useRef(null)
  const rowHeightRef = useRef(ESTIMATED_ROW_HEIGHT)
  const rowsMeasuredRef = useRef(false)

  useEffect(() => {
    let cancelled = false
    listExpenseYears().then((result) => {
      // Without the years the selector still offers the current one and "Todos".
      if (!cancelled && result.ok) {
        setYears(result.years)
      }
    })
    return () => {
      cancelled = true
    }
  }, [])

  const trimmed = term.trim()
  const from = `${location.pathname}${location.search}`

  const navigate = (next) => setSearchParams(paramsOf(next), { replace: true })

  // Puts the filters in force from page 1; a term that is too short counts as no term.
  const apply = (rawTerm, year, month) => {
    const value = rawTerm.trim()
    const nextTerm = isSearchable(value) ? value : ''
    if (nextTerm !== criteria.term || year !== criteria.year || month !== criteria.month) {
      navigate({ term: nextTerm, year, month, page: 1 })
    }
  }

  // Recomputes the page size; the new page keeps the first card in sight (FR-022).
  const refit = () => {
    const next = pageSizeFor(filtersRef.current, rowHeightRef.current)
    if (next === pageSize) {
      return
    }
    setPageSize(next)
    if (pageSize !== null) {
      const page = Math.floor(((criteria.page - 1) * pageSize) / next) + 1
      if (page !== criteria.page) {
        navigate({ ...criteria, page })
      }
    }
  }
  const refitOnEvent = useEffectEvent(refit)

  // The first size is known before the first query is sent.
  useLayoutEffect(() => {
    refitOnEvent()
  }, [])

  useEffect(() => {
    let timer
    const onResize = () => {
      clearTimeout(timer)
      timer = setTimeout(refitOnEvent, RESIZE_PAUSE_MS)
    }
    window.addEventListener('resize', onResize)
    return () => {
      clearTimeout(timer)
      window.removeEventListener('resize', onResize)
    }
  }, [])

  // The real card height replaces the estimate once the first cards are drawn.
  const handleListShown = (list) => {
    if (rowsMeasuredRef.current) {
      return
    }
    const height = rowHeightOf(list)
    if (height) {
      rowsMeasuredRef.current = true
      rowHeightRef.current = height
      refit()
    }
  }

  const applyTypedTerm = useEffectEvent((typed) => apply(typed, criteria.year, criteria.month))

  useEffect(() => {
    const timer = setTimeout(() => applyTypedTerm(term), DEBOUNCE_MS)
    return () => clearTimeout(timer)
  }, [term])

  const handleTermChange = (event) => {
    const value = event.target.value
    setTerm(value)
    // Erasing the text (e.g. the x of the field) shows the year right away (FR-010).
    if (value.trim() === '') {
      apply('', criteria.year, criteria.month)
    }
  }

  const handleSubmit = (event) => {
    event.preventDefault()
    apply(term, criteria.year, criteria.month)
  }

  const handleMonthChange = (event) => {
    apply(term, criteria.year, event.target.value)
  }

  // "Todos los años" has no months: the month goes back to all (FR-020).
  const handleYearChange = (event) => {
    const year = event.target.value
    apply(term, year, year === ALL_YEARS ? ALL_MONTHS : criteria.month)
  }

  const handlePage = (page) => {
    navigate({ ...criteria, page })
    topRef.current?.scrollIntoView?.({ block: 'start' })
  }

  const handlePageResolved = (page) => navigate({ ...criteria, page })

  // Typing a new term: "Buscando…" until the pause puts it in force.
  const isTyping = isSearchable(trimmed) && trimmed !== criteria.term

  return (
    <main className="page activity-page" ref={topRef}>
      <HomeLink />
      <h1>Actividad personal</h1>

      <form
        ref={filtersRef}
        className="activity-search"
        role="search"
        onSubmit={handleSubmit}
        noValidate
      >
        <div className="activity-search__row">
          <div className="activity-search__field">
            <SearchIcon />
            <input
              type="search"
              aria-label={SEARCH_PLACEHOLDER}
              placeholder={SEARCH_PLACEHOLDER}
              maxLength={MAX_LENGTH}
              autoComplete="off"
              enterKeyHint="search"
              value={term}
              onChange={handleTermChange}
            />
          </div>
          <div className="activity-search__selects">
            <select
              className="list-search__sort"
              aria-label="Mes"
              value={criteria.month}
              disabled={criteria.year === ALL_YEARS}
              onChange={handleMonthChange}
            >
              <option value={ALL_MONTHS}>Todos los meses</option>
              {MONTHS.map((name, index) => (
                <option key={name} value={String(index + 1)}>
                  {name}
                </option>
              ))}
            </select>
            <select
              className="list-search__sort"
              aria-label="Año"
              value={criteria.year}
              onChange={handleYearChange}
            >
              {yearOptionsOf(years, criteria.year).map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
              <option value={ALL_YEARS}>Todos los años</option>
            </select>
          </div>
        </div>
        {trimmed.length === 1 && <p className="activity-search__notice">{TOO_SHORT_MESSAGE}</p>}
      </form>

      {isTyping || pageSize === null ? (
        <p aria-busy="true">Buscando…</p>
      ) : (
        <ActivityResults
          key={`${criteria.term}|${criteria.year}|${criteria.month}`}
          term={criteria.term}
          year={criteria.year}
          month={criteria.month}
          page={criteria.page}
          size={pageSize}
          from={from}
          onPage={handlePage}
          onPageResolved={handlePageResolved}
          onListShown={handleListShown}
        />
      )}
    </main>
  )
}
