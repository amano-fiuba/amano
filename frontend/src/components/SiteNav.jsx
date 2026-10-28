import { Link, NavLink } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import ThemeToggle from './ThemeToggle'
import LogoutButton from './LogoutButton'

// Barra global de navegación (pedido fuera de specs): visible en todas las
// páginas. Los visitantes ven los accesos públicos y, con sesión, los accesos
// directos a mi perfil, al registro de gastos, a los gastos recurrentes
// (specs/009-pause-recurring-expense, FR-012), al buscador de usuarios
// (specs/008-user-search, FR-001) y a grupos (specs/010-groups-management).
const linkClass = ({ isActive }) =>
  isActive ? 'site-nav__link is-active' : 'site-nav__link'

export default function SiteNav() {
  const { user } = useAuth() ?? {}

  return (
    <header className="site-nav">
      <Link to="/" className="site-nav__brand" aria-label="A mano, ir al inicio">
        <img
          src="/logo.png"
          alt=""
          className="site-nav__logo"
          width={59}
          height={32}
        />
        A mano
      </Link>

      <nav className="site-nav__links" aria-label="Navegación principal">
        {user ? (
          <>
            <NavLink to="/users/search" className={linkClass}>
              Buscar usuarios
            </NavLink>
            <NavLink to="/groups" className={linkClass}>
              Grupos
            </NavLink>
            <NavLink to="/expenses/new" className={linkClass}>
              Registrar gasto
            </NavLink>
            <NavLink to="/expenses/recurring" className={linkClass}>
              Gastos recurrentes
            </NavLink>
            <NavLink to="/profile" className={linkClass}>
              Mi perfil
            </NavLink>
          </>
        ) : (
          <>
            <NavLink to="/" end className={linkClass}>
              Inicio
            </NavLink>
            <NavLink to="/login" className={linkClass}>
              Iniciar sesión
            </NavLink>
            <NavLink to="/register" className={linkClass}>
              Registrarse
            </NavLink>
          </>
        )}
      </nav>

      <ThemeToggle />

      {user && <LogoutButton />}
    </header>
  )
}
