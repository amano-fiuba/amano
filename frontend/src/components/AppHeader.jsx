import { Link, NavLink } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { userAvatarUrl } from '../auth/userAvatar'
import GlobalSearch from './GlobalSearch'
import NotificationsMenu from './NotificationsMenu'
import {
  ActivityIcon,
  FriendsIcon,
  GroupsIcon,
  SettingsIcon,
  StatisticsIcon,
} from './NavIcons'
import './AppHeader.scss'

const linkClass = ({ isActive }) =>
  isActive ? 'app-header__action is-active' : 'app-header__action'

function ActionLink({ to, label, children }) {
  return (
    <NavLink to={to} className={linkClass} aria-label={label} title={label}>
      {children}
    </NavLink>
  )
}

// Barra superior con sesión (specs/015-main-screen, US2): logo, buscador y
// accesos a perfil, actividad, estadísticas (032), grupos, amigos, notificaciones y ajustes.
export default function AppHeader() {
  const { user } = useAuth()

  return (
    <header className="app-header">
      <Link to="/" className="app-header__brand" aria-label="A mano, ir al inicio">
        <img src="/logo.png" alt="" width={59} height={32} />
        A mano
      </Link>

      <GlobalSearch />

      <nav className="app-header__actions" aria-label="Navegación principal">
        <ActionLink to="/profile" label="Mi perfil">
          <img src={userAvatarUrl(user)} alt="" width={40} height={40} />
        </ActionLink>
        <ActionLink to="/activity" label="Actividad personal">
          <ActivityIcon />
        </ActionLink>
        <ActionLink to="/statistics" label="Estadísticas">
          <StatisticsIcon />
        </ActionLink>
        <ActionLink to="/groups" label="Mis grupos">
          <GroupsIcon />
        </ActionLink>
        <ActionLink to="/friends" label="Amigos">
          <FriendsIcon />
        </ActionLink>
        <NotificationsMenu />
        <ActionLink to="/settings" label="Ajustes">
          <SettingsIcon />
        </ActionLink>
      </nav>
    </header>
  )
}
