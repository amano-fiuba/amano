import { Link, Navigate, useLocation, useNavigate } from 'react-router'
import LoginForm from '../components/LoginForm'
import { useAuth } from '../auth/AuthContext'

// Login page (feature 004). On success the profile goes into the shared
// session store and the user returns to where ProtectedRoute stopped them
// (state.from), or to the dashboard. Logged-in users skip the form.
export default function LoginPage() {
  const { user, loading, setUser } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()

  const destination = location.state?.from?.pathname ?? '/dashboard'

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
    <main>
      <h1>Iniciar sesión</h1>
      <LoginForm onLoggedIn={handleLoggedIn} />
      <p>
        ¿No tienes cuenta? <Link to="/register">Regístrate</Link>
      </p>
    </main>
  )
}
