import { useState } from 'react'
import { leaveGroup, GROUPS_NETWORK_ERROR_MESSAGE } from '../../api/groupsClient'

const LAST_ADMIN_MESSAGE = 'Sos el último administrador: asigná otro administrador antes de salir'

/**
 * Leave group (US5): confirmation gate before POST /api/groups/{id}/leave.
 * The last-admin motive can be anticipated client-side (suggestion to assign
 * another admin first), but the server decision always rules: rejection
 * motives come back as ProblemDetail and are rendered as-is.
 */
export default function LeaveGroupPanel({ groupId, isLastAdmin = false }) {
  const [open, setOpen] = useState(false)
  const [status, setStatus] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  function openDialog() {
    setStatus('')
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
      setOpen(false)
      setStatus('Se abandonó el grupo')
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
      {status && <p role="status">{status}</p>}
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
