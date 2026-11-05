import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { useAuth } from '../../auth/AuthContext'
import {
  GROUP_EXPENSE_LOAD_ERROR_MESSAGE,
  getGroupExpense,
} from '../../api/groupExpensesClient'
import ExpenseDebtList from '../../components/groups/ExpenseDebtList'
import { formatAmount, formatIsoDate } from '../../validation/expenseFormat'

export const EXPENSE_NOT_FOUND_MESSAGE = 'No existe el gasto solicitado o no sos miembro del grupo'

// Detail of a group expense with its shares and debts (specs/017-debt-states,
// US3, FR-020/FR-023/FR-025). Names are the copies stored with the expense, so
// a simulated profile removed later still shows up.
export default function GroupExpenseDetailView() {
  const { id, expenseId } = useParams()
  const auth = useAuth()
  const setUser = auth?.setUser
  const [retryToken, setRetryToken] = useState(0)
  const [state, setState] = useState({ status: 'loading', expense: null, error: '' })

  useEffect(() => {
    let cancelled = false
    getGroupExpense(id, expenseId).then((result) => {
      if (cancelled) return
      if (result.ok) {
        setState({ status: 'ready', expense: result.expense, error: '' })
        return
      }
      if (result.status === 401) {
        setUser?.(null)
        return
      }
      setState({
        status: 'error',
        expense: null,
        error: result.network ? GROUP_EXPENSE_LOAD_ERROR_MESSAGE : EXPENSE_NOT_FOUND_MESSAGE,
        retryable: Boolean(result.network),
      })
    })
    return () => {
      cancelled = true
    }
  }, [id, expenseId, retryToken, setUser])

  const { status, expense, error, retryable } = state
  const shares = Array.isArray(expense?.shares) ? expense.shares : []

  return (
    <main className="page group-page group-expense">
      <nav className="group-page__back">
        <Link to={`/groups/${id}`}>Volver al grupo</Link>
      </nav>

      {status === 'loading' && (
        <div role="status" className="group-expense__loading">
          <span className="button-spinner" aria-hidden="true" />
          <p>Cargando…</p>
        </div>
      )}

      {status === 'error' && (
        <div role="alert" data-testid="group-expense-detail-error">
          <p>{error}</p>
          {retryable && (
            <button
              type="button"
              onClick={() => {
                setState({ status: 'loading', expense: null, error: '' })
                setRetryToken((token) => token + 1)
              }}
            >
              Reintentar
            </button>
          )}
        </div>
      )}

      {status === 'ready' && expense && (
        <>
          <header className="group-card group-expense__header" data-testid="group-expense-detail">
            <h1>{expense.description || 'Gasto sin descripción'}</h1>
            <p className="group-expense__total">
              {expense.currency} $ {formatAmount(expense.amount)}
            </p>
            <p>
              {formatIsoDate(expense.date)} · Pagó {expense.payer?.name ?? 'alguien'}
            </p>
          </header>

          <section className="group-card" aria-label="Partes del gasto">
            <h3>División</h3>
            <ul className="group-expense__shares">
              {shares.map((share) => (
                <li key={share.position} data-testid="share-row">
                  <span>
                    {share.name}
                    {share.isPayer && ' (pagó)'}
                  </span>
                  <span>$ {formatAmount(share.amount)}</span>
                </li>
              ))}
            </ul>
          </section>

          <section className="group-card" aria-label="Deudas">
            <h3>Deudas</h3>
            <ExpenseDebtList debts={expense.debts} groupId={id} expenseId={expenseId} />
          </section>
        </>
      )}
    </main>
  )
}
