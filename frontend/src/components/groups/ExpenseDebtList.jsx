import DebtStatusBadge from './DebtStatusBadge'
import { formatAmount } from '../../validation/expenseFormat'

export const NO_DEBTS_MESSAGE = 'Este gasto no generó deudas'

// Debts of one group expense (specs/017-debt-states, FR-023): "{deudor} le debe
// a {acreedor}", the amount and the state. Read-only: payments belong to 018.
// The backend already sends them in share order (contract §GET).
export default function ExpenseDebtList({ debts }) {
  const list = Array.isArray(debts) ? debts : []
  if (list.length === 0) {
    return <p className="group-expense__empty">{NO_DEBTS_MESSAGE}</p>
  }
  return (
    <ul className="group-expense__debts" aria-label="Deudas del gasto">
      {list.map((debt) => (
        <li key={debt.id} className="group-expense__debt" data-testid="debt-row">
          <span className="group-expense__debt-who">
            {debt.debtor?.name ?? 'Alguien'} le debe a {debt.creditor?.name ?? 'alguien'}
          </span>
          <span className="group-expense__debt-amount">$ {formatAmount(debt.amount)}</span>
          <DebtStatusBadge status={debt.status} outstanding={debt.outstanding} />
        </li>
      ))}
    </ul>
  )
}
