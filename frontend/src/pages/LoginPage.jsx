import { Link, Navigate, useLocation, useNavigate } from 'react-router'
import LoginForm from '../components/LoginForm'
import { useAuth } from '../auth/AuthContext'

// Login page (feature 004). On success the profile goes into the shared
// session store and the user returns to where ProtectedRoute stopped them
// (state.from), or to the dashboard. Logged-in users skip the form. A notice
// passed in state.notice (feature 006: password updated) is shown above the form.
export default function LoginPage() {
  const { user, loading, setUser } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()

  const destination = location.state?.from?.pathname ?? '/dashboard'
  const notice = location.state?.notice

  if (loading) {
    return null
  }
  if (user) {
    return <Navigate to={destination} replace />
  }

  const handleLoggedIn = (profile) => {
    setUser(profile)
    navigate(destination, { replace: true })
  }

  return (
    <main className="page">
      <h1>Iniciar sesión</h1>
      {notice && (
        <p role="status" data-testid="login-notice">
          {notice}
        </p>
      )}
      <LoginForm onLoggedIn={handleLoggedIn} />
      <nav aria-label="Enlaces de inicio de sesión">
        <p>
          <Link to="/forgot-password">¿Olvidaste tu contraseña?</Link>
        </p>
        <p>
          ¿No tenés cuenta? <Link to="/register">Regístrate</Link>
        </p>
      </nav>
    </main>
  )
}
