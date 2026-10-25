import { NavLink } from 'react-router'
import ThemeToggle from './ThemeToggle'

// Barra global de navegación (pedido fuera de specs): visible en todas las
// páginas, con accesos directos al dashboard, al registro de gastos, a los gastos
// recurrentes (specs/009-pause-recurring-expense, FR-012) y al buscador de usuarios
// (specs/008-user-search, FR-001). Todas las rutas son protegidas, así que sin
// sesión ProtectedRoute redirige al login.
const linkClass = ({ isActive }) =>
  isActive ? 'site-nav__link is-active' : 'site-nav__link'

export default function SiteNav() {
  return (
    <header className="site-nav">
      <nav className="site-nav__links" aria-label="Navegación principal">
        <NavLink to="/dashboard" className={linkClass}>
          Dashboard
        </NavLink>
        <NavLink to="/expenses/new" className={linkClass}>
          Registrar gasto
        </NavLink>
        <NavLink to="/expenses/recurring" className={linkClass}>
          Gastos recurrentes
        </NavLink>
        <NavLink to="/users/search" className={linkClass}>
          Buscar usuarios
        </NavLink>
      </nav>
      <ThemeToggle />
    </header>
  )
}