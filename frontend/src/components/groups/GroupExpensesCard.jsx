import { Link } from 'react-router'
import { expensePermissions } from '../../pages/groups/expensePermissions'

export const NO_EXPENSES_MESSAGE = 'Todavía no hay gastos'

function countLabel(count) {
  if (!count) {
    return NO_EXPENSES_MESSAGE
  }
  return count === 1 ? '1 gasto registrado' : `${count} gastos registrados`
}

// "Gastos" card of the group screen (specs/017-debt-states, FR-021): number of
// expenses, "Nuevo gasto" when the role and the casillas allow it, and the
// history filtered by expenses. It uses the group the view already loaded.
export default function GroupExpensesCard({ groupId, group }) {
  const { canCreateExpenses } = expensePermissions(group)
  return (
    <section className="group-card group-expenses-card" aria-label="Gastos del grupo">
      <h3>Gastos</h3>
      <p data-testid="group-expenses-count">{countLabel(group?.expensesCount)}</p>
      <div className="group-actions-row">
        {canCreateExpenses && (
          <Link to={`/groups/${groupId}/expenses/new`} className="group-expenses-card__new">
            Nuevo gasto
          </Link>
        )}
        <Link to={`/groups/${groupId}/history?filter=EXPENSES`}>Ver gastos en el historial</Link>
      </div>
    </section>
  )
}
