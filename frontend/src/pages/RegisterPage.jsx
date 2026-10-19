import { useNavigate } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import RegisterForm from '../components/RegisterForm'

// Registration page (task T023): on a successful registration it populates the
// session (automatic login, FR-016) and navigates to the dashboard (SC-001).
export default function RegisterPage() {
  const { setUser } = useAuth()
  const navigate = useNavigate()

  const handleRegistered = (profile) => {
    setUser(profile)
    navigate('/dashboard')
  }

  return (
    <main>
      <h1>Crear tu cuenta</h1>
      <RegisterForm onRegistered={handleRegistered} />
    </main>
  )
}
