import { BrowserRouter, Navigate, Route, Routes } from 'react-router'
import { AuthProvider, useAuth } from './auth/AuthContext'
import ProtectedRoute from './auth/ProtectedRoute'
import HomePage from './pages/HomePage'
import MainPage from './pages/MainPage'
import SettingsPage from './pages/SettingsPage'
import FaqPage from './pages/FaqPage'
import ActivityPage from './pages/ActivityPage'
import RegisterPage from './pages/RegisterPage'
import LoginPage from './pages/LoginPage'
import CheckEmailPage from './pages/CheckEmailPage'
import VerifyEmailPage from './pages/VerifyEmailPage'
import ExpiredLinkPage from './pages/ExpiredLinkPage'
import ForgotPasswordPage from './pages/ForgotPasswordPage'
import ResetPasswordPage from './pages/ResetPasswordPage'
import ProfilePage from './pages/ProfilePage'
import ProfileEditPage from './pages/ProfileEditPage'
import ChangePasswordPage from './pages/ChangePasswordPage'
import UsersSearchPage from './pages/UsersSearchPage'
import FriendsPage from './pages/FriendsPage'
import NewExpensePage from './pages/NewExpensePage'
import RecurringExpensesPage from './pages/RecurringExpensesPage'
import GroupRecurringExpensesPage from './pages/groups/GroupRecurringExpensesPage'
import GroupListView from './pages/groups/GroupListView'
import CreateGroupView from './pages/groups/CreateGroupView'
import GroupInfoView from './pages/groups/GroupInfoView'
import GroupHistoryView from './pages/groups/GroupHistoryView'
import GroupExpenseFormView from './pages/groups/GroupExpenseFormView'
import FriendExpenseFormView from './pages/FriendExpenseFormView'
import GroupDebtsView from './pages/groups/GroupDebtsView'
import GroupExpenseDetailView from './pages/groups/GroupExpenseDetailView'
import RegisterPaymentView from './pages/groups/RegisterPaymentView'
import { useParams } from 'react-router'
import SiteNav from './components/SiteNav'
import QuickActionsButton from './components/QuickActionsButton'
import { SnackbarProvider } from './components/Snackbar'

// Rutas base del shell de la SPA (T015 + US1 + US2): '/', '/register',
// '/login' (specs/004-user-login), '/check-email' (pantalla "Revisa tu correo"),
// '/verify-email' (canje del enlace), '/verify-email/expired' (enlace vencido),
// '/forgot-password' y '/reset-password' (specs/006-password-recovery), todas
// públicas por diseño; '/profile' y sus rutas de edición y de
// cambio de contraseña (specs/005-edit-user-profile) quedan protegidas.
function GroupHistoryPage() {
  const { id } = useParams()
  return <GroupHistoryView groupId={id} />
}

// '/' es la landing sin sesión y la pantalla principal con sesión
// (specs/015-main-screen, FR-001). Mientras se verifica la sesión no se muestra
// ninguna de las dos, para evitar el destello de la landing.
function RootRoute() {
  const { user, loading } = useAuth()
  if (loading) {
    return null
  }
  return user ? <MainPage /> : <HomePage />
}

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<RootRoute />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/check-email" element={<CheckEmailPage />} />
      <Route path="/verify-email" element={<VerifyEmailPage />} />
      <Route path="/verify-email/expired" element={<ExpiredLinkPage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />
      {/* El dashboard se retiró; la ruta vieja redirige para no romper URLs guardadas. */}
      <Route path="/dashboard" element={<Navigate to="/" replace />} />
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
      {/* Gasto con un amigo (specs/027-expense-entry-modes, US4). */}
      <Route
        path="/friends/:friendId/expenses/new"
        element={
          <ProtectedRoute>
            <FriendExpenseFormView />
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
      {/* Amigos y solicitudes pendientes (specs/011-send-friend-request, FR-010). */}
      <Route
        path="/friends"
        element={
          <ProtectedRoute>
            <FriendsPage />
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
      {/* Gastos de grupo y deudas (specs/017-debt-states, US3). */}
      <Route
        path="/groups/:id/debts"
        element={
          <ProtectedRoute>
            <GroupDebtsView />
          </ProtectedRoute>
        }
      />
      <Route
        path="/groups/:id/recurring-expenses"
        element={
          <ProtectedRoute>
            <GroupRecurringExpensesPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/groups/:id/expenses/new"
        element={
          <ProtectedRoute>
            <GroupExpenseFormView />
          </ProtectedRoute>
        }
      />
      <Route
        path="/groups/:id/expenses/:expenseId/edit"
        element={
          <ProtectedRoute>
            <GroupExpenseFormView />
          </ProtectedRoute>
        }
      />
      <Route
        path="/groups/:id/expenses/:expenseId"
        element={
          <ProtectedRoute>
            <GroupExpenseDetailView />
          </ProtectedRoute>
        }
      />
      <Route
        path="/groups/:id/expenses/:expenseId/debts/:debtId/payment"
        element={
          <ProtectedRoute>
            <RegisterPaymentView />
          </ProtectedRoute>
        }
      />
      {/* Ajustes y FAQ (specs/015-main-screen, FR-009). */}
      <Route
        path="/settings"
        element={
          <ProtectedRoute>
            <SettingsPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/activity"
        element={
          <ProtectedRoute>
            <ActivityPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/faq"
        element={
          <ProtectedRoute>
            <FaqPage />
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
        <SnackbarProvider>
          <SiteNav />
          <AppRoutes />
          <QuickActionsButton />
        </SnackbarProvider>
      </BrowserRouter>
    </AuthProvider>
  )
}
