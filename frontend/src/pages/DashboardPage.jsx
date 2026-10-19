import { useEffect } from 'react'
import { me } from '../api/authClient'
import { useAuth } from '../auth/AuthContext'

// Protected landing screen (task T024; FR-016, FR-021, SC-002): renders the
// session profile and refreshes it via the users contract (GET /api/users/me).
// currencies empty → display default ["ARS"] (FR-011/Q1).
export default function DashboardPage() {
  const { user, setUser } = useAuth()

  useEffect(() => {
    let cancelled = false
    me().then((result) => {
      if (!cancelled && result.ok) {
        setUser(result.profile)
      }
    })
    return () => {
      cancelled = true
    }
  }, [setUser])

  if (!user) {
    return null
  }

  const avatarUrl =
    user.hasCustomAvatar && user.avatarUrl ? user.avatarUrl : '/avatar-default.png'
  const currencies =
    Array.isArray(user.currencies) && user.currencies.length > 0
      ? user.currencies
      : ['ARS']
  const fullName = user.lastName ? `${user.firstName} ${user.lastName}` : user.firstName

  return (
    <main>
      <img src={avatarUrl} alt="@avatar" width={96} height={96} />
      <h1>Bienvenida, {fullName}</h1>
      <p data-testid="dashboard-email">{user.email}</p>
      {/* FR-009: the CBU/CVU is only shown to the session owner (FR-021), here. */}
      {user.cbuCvu && <p data-testid="dashboard-cbu">CBU/CVU: {user.cbuCvu}</p>}
      <p data-testid="dashboard-currencies">Monedas: {currencies.join(', ')}</p>
    </main>
  )
}
