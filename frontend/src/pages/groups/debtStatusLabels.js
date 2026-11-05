import { formatAmount } from '../../validation/expenseFormat'

// Spanish labels of the debt states (specs/017-debt-states, data-model.md
// §Estados de deuda, FR-023). The CSS modifier is exported too so the badge and
// its tests agree on one mapping.

export const DEBT_STATUS_UNKNOWN = 'Estado desconocido'

const MODIFIERS = {
  PENDING: 'pending',
  PARTIALLY_PAID: 'partial',
  PAID: 'paid',
}

/** "Pendiente", "Pago parcial · faltan $ 1.234,50", "Pagada" or the unknown label. */
export function debtStatusLabel(status, outstanding) {
  switch (status) {
    case 'PENDING':
      return 'Pendiente'
    case 'PARTIALLY_PAID':
      return `Pago parcial · faltan $ ${formatAmount(outstanding)}`
    case 'PAID':
      return 'Pagada'
    default:
      return DEBT_STATUS_UNKNOWN
  }
}

/** BEM modifier of `.group-expense__status`, or "unknown". */
export function debtStatusModifier(status) {
  return MODIFIERS[status] ?? 'unknown'
}
