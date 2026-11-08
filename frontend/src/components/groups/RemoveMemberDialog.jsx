import { useEffect, useRef, useState } from 'react'
import { removeMember, removeSimulatedProfile, GROUPS_NETWORK_ERROR_MESSAGE } from '../../api/groupsClient'
import { useSnackbar } from '../Snackbar'

const ADMIN_TARGET_MESSAGE =
  'No podés eliminar directamente a otro administrador: primero quitale el rol'

/**
 * Confirmation modal before removal (FR-009): distinct handling for another
 * admin ("revoke the role first"), pending-debts and simulated-profile cases.
 * The group views render it with either a registered member (`member` prop)
 * or a `simulatedProfile` — both share the confirm step. Same dialog pattern
 * and styles as LeaveGroupModal: another admin only gets the close button. On success it calls
 * `onRemoved` (the list updates by itself) and then `onClose`.
 */
export default function RemoveMemberDialog({ groupId, member, simulatedProfile, onRemoved, onClose }) {
  const showSnackbar = useSnackbar()
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const closeRef = useRef(null)

  const isAdminTarget = member?.role === 'ADMIN'
  const targetName = simulatedProfile?.name ?? member?.name ?? ''

  useEffect(() => {
    closeRef.current?.focus()
  }, [])

  useEffect(() => {
    function handleKeyDown(event) {
      if (event.key === 'Escape' && !busy) {
        onClose?.()
      }
    }
    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [busy, onClose])

  async function confirmRemove() {
    setBusy(true)
    setError('')
    const result = simulatedProfile
      ? await removeSimulatedProfile(groupId, simulatedProfile.profileId)
      : await removeMember(groupId, member.userId)
    if (!result || result.network) {
      setError(GROUPS_NETWORK_ERROR_MESSAGE)
    } else if (result.ok) {
      showSnackbar(`${targetName} ya no forma parte del grupo`)
      // the parent list re-fetches itself: no manual refresh (fix 2026-11-04)
      onRemoved?.()
      onClose?.()
      return
    } else {
      setError(result.detail || 'No se pudo eliminar')
    }
    setBusy(false)
  }

  const title = simulatedProfile ? 'Eliminar perfil simulado' : 'Eliminar miembro'
  const message = isAdminTarget
    ? ADMIN_TARGET_MESSAGE
    : `¿Seguro que querés eliminar ${simulatedProfile ? 'al perfil simulado' : 'a'} ${targetName}?`

  return (
    <div className="delete-friend-dialog__backdrop">
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="remove-member-title"
        aria-describedby="remove-member-message"
        className="delete-friend-dialog leave-group-modal"
        data-testid="remove-member-dialog"
      >
        <button
          type="button"
          ref={closeRef}
          className="leave-group-modal__close"
          aria-label="Cerrar"
          disabled={busy}
          onClick={() => onClose?.()}
        >
          <span aria-hidden="true">×</span>
        </button>
        <h3 id="remove-member-title">{title}</h3>
        <p
          id="remove-member-message"
          data-testid={isAdminTarget ? 'remove-member-admin-hint' : 'remove-member-message'}
        >
          {message}
        </p>
        {error && (
          <p className="delete-friend-dialog__error" role="alert" data-testid="remove-member-error">
            {error}
          </p>
        )}
        {!isAdminTarget && (
          <div className="delete-friend-dialog__actions">
            <button
              type="button"
              className="delete-friend-dialog__cancel"
              disabled={busy}
              onClick={() => onClose?.()}
            >
              Cancelar
            </button>
            <button
              type="button"
              className="delete-friend-dialog__confirm"
              disabled={busy}
              onClick={confirmRemove}
            >
              Confirmar
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
