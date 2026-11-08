import { Link } from 'react-router'
import { listExpenseHistory, RECENT_EXPENSES_ERROR_MESSAGE } from '../api/expenseClient'
import { useSessionData } from '../hooks/useSessionData'
import { CATEGORY_OPTIONS, labelOf } from '../validation/expenseCatalog'
import { formatAmount, formatIsoDate } from '../validation/expenseFormat'
import './MainPage.scss'
import HomeLink from '../components/HomeLink'

// Pantalla de actividad personal: los últimos 200 gastos propios.
export default function ActivityPage() {
  const [{ status, items }] = useSessionData(listExpenseHistory, 'expenses')

  return (
    <main className="page">
      <HomeLink />
      <h1>Actividad personal</h1>

      {status === 'loading' && <p aria-busy="true">Cargando…</p>}
      {status === 'error' && <p role="alert">{RECENT_EXPENSES_ERROR_MESSAGE}</p>}
      {status === 'ready' && items.length === 0 && (
        <p>
          Todavía no registraste gastos. <Link to="/expenses/new">Registrar un gasto</Link>
        </p>
      )}
      {items.length > 0 && (
        <ul className="main-list">
          {items.map((expense) => (
            <li key={expense.id} className="main-expense">
              <span>
                <strong>{expense.description || labelOf(CATEGORY_OPTIONS, expense.category)}</strong>
                <small>{formatIsoDate(expense.date)}</small>
              </span>
              <span className="main-expense__amount">
                {expense.currency} {formatAmount(expense.amount)}
              </span>
            </li>
          ))}
        </ul>
      )}
    </main>
  )
}
