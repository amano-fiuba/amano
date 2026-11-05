import { useState } from 'react'
import { removeMember, removeSimulatedProfile, GROUPS_NETWORK_ERROR_MESSAGE } from '../../api/groupsClient'
import { useSnackbar } from '../Snackbar'

const ADMIN_TARGET_MESSAGE =
  'No podés eliminar directamente a otro administrador: primero quitale el rol'

/**
 * Confirmation dialog before removal (FR-009): distinct handling for another
 * admin ("revoke the role first"), pending-debts and simulated-profile cases.
 * The dialog is rendered by the group views with either a registered member
 * (`member` prop) or a `simulatedProfile` — both share the confirm step.
 * On success it closes and calls `onRemoved` so the list updates by itself.
 */
export default function RemoveMemberDialog({ groupId, member, simulatedProfile, onRemoved }) {
  const showSnackbar = useSnackbar()
  const [open, setOpen] = useState(false)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const isAdminTarget = member?.role === 'ADMIN'
  const targetName = simulatedProfile?.name ?? member?.name ?? ''

  function openDialog() {
    setError('')
    setOpen(true)
  }

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
      setOpen(false)
      // the parent list re-fetches itself: no manual refresh (fix 2026-11-04)
      onRemoved?.()
    } else {
      setError(result.detail || 'No se pudo eliminar')
    }
    setBusy(false)
  }

  return (
    <section data-testid="remove-member">
      <button type="button" onClick={openDialog}>
        {simulatedProfile ? 'Eliminar perfil simulado' : 'Eliminar miembro'}
      </button>
      {error && (
        <p role="alert" data-testid="remove-member-error">
          {error}
        </p>
      )}
      {open && (
        <div
          role="dialog"
          aria-label={`Eliminar a ${targetName}`}
          data-testid="remove-member-dialog"
        >
          <p>
            ¿Seguro que querés eliminar {simulatedProfile ? `al perfil simulado` : 'a'} {targetName}?
          </p>
          {isAdminTarget && (
            <p role="alert" data-testid="remove-member-admin-hint">
              {ADMIN_TARGET_MESSAGE}
            </p>
          )}
          {!isAdminTarget && (
            <button type="button" onClick={confirmRemove} disabled={busy}>
              Confirmar
            </button>
          )}
          <button type="button" onClick={() => setOpen(false)}>
            Cancelar
          </button>
        </div>
      )}
    </section>
  )
}
