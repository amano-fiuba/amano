import { NavLink } from 'react-router'
import ThemeToggle from './ThemeToggle'

// Barra global de navegación (pedido fuera de specs): visible en todas las
// páginas, con accesos directos al dashboard y al registro de gastos. Ambas
// rutas son protegidas, así que sin sesión ProtectedRoute redirige al login.
export default function SiteNav() {
  return (
    <header className="site-nav">
      <nav className="site-nav__links" aria-label="Navegación principal">
        <NavLink
          to="/dashboard"
          className={({ isActive }) =>
            isActive ? 'site-nav__link is-active' : 'site-nav__link'
          }
        >
          Dashboard
        </NavLink>
        <NavLink
          to="/expenses/new"
          className={({ isActive }) =>
            isActive ? 'site-nav__link is-active' : 'site-nav__link'
          }
        >
          Registrar gasto
        </NavLink>
      </nav>
      <ThemeToggle />
    </header>
  )
}
