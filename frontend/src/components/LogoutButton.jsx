import { useNavigate } from 'react-router'
import { logout } from '../api/authClient'
import { useAuth } from '../auth/AuthContext'

// Cerrar sesión en la barra global (pedido fuera de specs): con el backend
// expirando la cookie HttpOnly, la sesión local se limpia pase lo que pase
// para nunca quedar a medias (mismo contrato que tenía el dashboard retirado);
// vuelve al login.
export default function LogoutButton() {
  const { setUser } = useAuth()
  const navigate = useNavigate()

  const handleLogout = async () => {
    // El outcome del backend no bloquea: la cookie HttpOnly ya se expiró (o
    // expira aunque falle la fetch), así que se limpia la sesión igual.
    try {
      await logout()
    } finally {
      setUser(null)
      navigate('/login', { replace: true })
    }
  }

  return (
    <button type="button" className="site-nav__logout" onClick={handleLogout}>
      Cerrar sesión
    </button>
  )
}
