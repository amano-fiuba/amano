import { useState } from 'react'
import { useNavigate } from 'react-router'
import { leaveGroup, GROUPS_NETWORK_ERROR_MESSAGE } from '../../api/groupsClient'
import { useSnackbar } from '../Snackbar'

const LAST_ADMIN_MESSAGE = 'Sos el último administrador: asigná otro administrador antes de salir'

/**
 * Leave group (US5): confirmation gate before POST /api/groups/{id}/leave.
 * The last-admin motive can be anticipated client-side (suggestion to assign
 * another admin first), but the server decision always rules: rejection
 * motives come back as ProblemDetail and are rendered as-is. On success the
 * user goes to the groups list (FR-015).
 */
export default function LeaveGroupPanel({ groupId, isLastAdmin = false }) {
  const navigate = useNavigate()
  const showSnackbar = useSnackbar()
  const [open, setOpen] = useState(false)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  function openDialog() {
    setError('')
    setOpen(true)
  }

  async function confirmLeave() {
    setBusy(true)
    setError('')
    const result = await leaveGroup(groupId)
    if (!result || result.network) {
      setError(GROUPS_NETWORK_ERROR_MESSAGE)
    } else if (result.ok) {
      showSnackbar('Se abandonó el grupo')
      // replace: "atrás" no debe volver a un grupo del que ya no se es miembro.
      navigate('/groups', { replace: true })
    } else {
      setError(result.detail || 'No se pudo abandonar el grupo')
    }
    setBusy(false)
  }

  return (
    <section data-testid="leave-group">
      <button type="button" onClick={openDialog}>
        Abandonar grupo
      </button>
      {error && (
        <p role="alert" data-testid="leave-group-error">
          {error}
        </p>
      )}
      {open && (
        <div role="dialog" aria-label="Abandonar el grupo" data-testid="leave-group-dialog">
          {isLastAdmin ? (
            <p role="alert" data-testid="leave-group-last-admin-hint">
              {LAST_ADMIN_MESSAGE}
            </p>
          ) : (
            <>
              <p>¿Seguro que querés abandonar el grupo? Vas a dejar de ver sus gastos.</p>
              <button type="button" onClick={confirmLeave} disabled={busy}>
                Confirmar
              </button>
            </>
          )}
          <button type="button" onClick={() => setOpen(false)}>
            Cancelar
          </button>
        </div>
      )}
    </section>
  )
}
