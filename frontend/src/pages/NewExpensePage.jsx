import { useEffect, useState } from 'react'
import { me } from '../api/authClient'
import ExpenseForm from '../components/ExpenseForm'
import useCurrentDate from '../hooks/useCurrentDate'
import { preferredCurrency } from '../validation/expenseValidation'

export const PROFILE_FALLBACK_NOTICE =
  'No se pudo cargar tu moneda preferida. Se usa ARS.'

/**
 * New expense screen (T027, FR-001/FR-009/FR-011): protected route, date of the
 * device zone preloaded by the hook and the profile currency resolved with
 * me(). Any unreadable profile falls back to ARS; only a network/5xx failure
 * shows the non-blocking notice (a 401 is handled by ProtectedRoute).
 */
export default function NewExpensePage() {
  const { today } = useCurrentDate()
  const [currency, setCurrency] = useState('ARS')
  const [notice, setNotice] = useState('')

  useEffect(() => {
    let cancelled = false
    me().then((result) => {
      if (cancelled) {
        return
      }
      if (result.ok) {
        setCurrency(preferredCurrency(result.profile?.currencies))
      } else if (result.network) {
        setNotice(PROFILE_FALLBACK_NOTICE)
      }
    })
    return () => {
      cancelled = true
    }
  }, [])

  return (
    <main className="page">
      <h1>Nuevo gasto</h1>
      {notice && (
        <p role="status" data-testid="currency-notice">
          {notice}
        </p>
      )}
      <ExpenseForm today={today} initialCurrency={currency} />
    </main>
  )
}
