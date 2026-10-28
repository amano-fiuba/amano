import { BrowserRouter, Route, Routes } from 'react-router'
import { AuthProvider } from './auth/AuthContext'
import ProtectedRoute from './auth/ProtectedRoute'
import HomePage from './pages/HomePage'
import RegisterPage from './pages/RegisterPage'
import LoginPage from './pages/LoginPage'
import CheckEmailPage from './pages/CheckEmailPage'
import VerifyEmailPage from './pages/VerifyEmailPage'
import ExpiredLinkPage from './pages/ExpiredLinkPage'
import ForgotPasswordPage from './pages/ForgotPasswordPage'
import ResetPasswordPage from './pages/ResetPasswordPage'
import DashboardPage from './pages/DashboardPage'
import ProfilePage from './pages/ProfilePage'
import ProfileEditPage from './pages/ProfileEditPage'
import ChangePasswordPage from './pages/ChangePasswordPage'
import UsersSearchPage from './pages/UsersSearchPage'
import NewExpensePage from './pages/NewExpensePage'
import RecurringExpensesPage from './pages/RecurringExpensesPage'
import GroupListView from './pages/groups/GroupListView'
import CreateGroupView from './pages/groups/CreateGroupView'
import GroupInfoView from './pages/groups/GroupInfoView'
import GroupHistoryView from './pages/groups/GroupHistoryView'
import { useParams } from 'react-router'
import SiteNav from './components/SiteNav'

// Rutas base del shell de la SPA (T015 + US1 + US2): '/', '/register',
// '/login' (specs/004-user-login), '/check-email' (pantalla "Revisa tu correo"),
// '/verify-email' (canje del enlace), '/verify-email/expired' (enlace vencido),
// '/forgot-password' y '/reset-password' (specs/006-password-recovery), todas
// públicas por diseño; '/dashboard', '/profile' y sus rutas de edición y de
// cambio de contraseña (specs/005-edit-user-profile) quedan protegidas.
function GroupHistoryPage() {
  const { id } = useParams()
  return <GroupHistoryView groupId={id} />
}

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/check-email" element={<CheckEmailPage />} />
      <Route path="/verify-email" element={<VerifyEmailPage />} />
      <Route path="/verify-email/expired" element={<ExpiredLinkPage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />
      <Route
        path="/dashboard"
        element={
          <ProtectedRoute>
            <DashboardPage />
          </ProtectedRoute>
        }
      />
      {/* Ruta protegida del perfil (T005; FR-001, FR-008): sin sesión redirige. */}
      {/* Registro de gasto (specs/007-register-expense, FR-001/FR-002). */}
      <Route
        path="/expenses/new"
        element={
          <ProtectedRoute>
            <NewExpensePage />
          </ProtectedRoute>
        }
      />
      {/* Gastos recurrentes: pausar o finalizar (specs/009-pause-recurring-expense, FR-012). */}
      <Route
        path="/expenses/recurring"
        element={
          <ProtectedRoute>
            <RecurringExpensesPage />
          </ProtectedRoute>
        }
      />
      {/* Buscador de usuarios (specs/008-user-search, FR-001/FR-011). */}
      <Route
        path="/users/search"
        element={
          <ProtectedRoute>
            <UsersSearchPage />
          </ProtectedRoute>
        }
      />
      {/* Grupos (specs/010-groups-management, US1): listado, creación y detalle. */}
      <Route
        path="/groups"
        element={
          <ProtectedRoute>
            <GroupListView />
          </ProtectedRoute>
        }
      />
      <Route
        path="/groups/new"
        element={
          <ProtectedRoute>
            <CreateGroupView />
          </ProtectedRoute>
        }
      />
      <Route
        path="/groups/:id"
        element={
          <ProtectedRoute>
            <GroupInfoView />
          </ProtectedRoute>
        }
      />
      {/* Grupos (specs/010-groups-management, US9): historial de actividad. */}
      <Route
        path="/groups/:id/history"
        element={
          <ProtectedRoute>
            <GroupHistoryPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/profile"
        element={
          <ProtectedRoute>
            <ProfilePage />
          </ProtectedRoute>
        }
      />
      {/* Edición del perfil (specs/005-edit-user-profile, FR-001/FR-018). */}
      <Route
        path="/profile/edit"
        element={
          <ProtectedRoute>
            <ProfileEditPage />
          </ProtectedRoute>
        }
      />
      {/* Cambio de contraseña (US2 de 005). */}
      <Route
        path="/profile/password"
        element={
          <ProtectedRoute>
            <ChangePasswordPage />
          </ProtectedRoute>
        }
      />
    </Routes>
  )
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <SiteNav />
        <AppRoutes />
      </BrowserRouter>
    </AuthProvider>
  )
}
