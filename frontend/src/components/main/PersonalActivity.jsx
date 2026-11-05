import { RECENT_EXPENSES_ERROR_MESSAGE, listRecentExpenses } from '../../api/expenseClient'
import { useSessionData } from '../../hooks/useSessionData'
import { CATEGORY_OPTIONS, labelOf } from '../../validation/expenseCatalog'
import { formatAmount, formatIsoDate } from '../../validation/expenseFormat'

// Últimos gastos registrados (specs/015-main-screen, FR-008).
export default function PersonalActivity() {
  const [{ status, items }] = useSessionData(listRecentExpenses, 'expenses')

  return (
    <section className="main-card" aria-labelledby="main-activity-title">
      <div className="main-card__header">
        <h2 id="main-activity-title">Actividad personal</h2>
      </div>

      {status === 'loading' && <p aria-busy="true">Cargando…</p>}
      {status === 'error' && <p role="alert">{RECENT_EXPENSES_ERROR_MESSAGE}</p>}
      {status === 'ready' && items.length === 0 && <p>Todavía no registraste gastos</p>}
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
    </section>
  )
}
