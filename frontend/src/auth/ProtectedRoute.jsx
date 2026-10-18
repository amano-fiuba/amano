import { Navigate, useLocation } from 'react-router'
import { useAuth } from './AuthContext'

// Gate for protected routes (task T015; SC-002): while the session check is
// in flight nothing renders; without a session the user is redirected to
// /register, preserving the attempted destination.
export default function ProtectedRoute({ children }) {
  const { user, loading } = useAuth()
  const location = useLocation()

  if (loading) {
    return null
  }
  if (!user) {
    return <Navigate to="/register" replace state={{ from: location }} />
  }
  return children
}
