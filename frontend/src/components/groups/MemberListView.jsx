import { useEffect, useState } from 'react'
import {
  getMembers,
  grantAdminRole,
  revokeAdminRole,
  GROUPS_NETWORK_ERROR_MESSAGE,
} from '../../api/groupsClient'
import RemoveMemberDialog from './RemoveMemberDialog'

const DEFAULT_AVATAR = '/avatar-default.png'
const LAST_ADMIN = 'Sos el último administrador: asigná otro administrador antes de salir'

/**
 * Members list (US6, T079): every ACTIVE member — registered users and
 * simulated profiles — with name, avatar (NULL → gray default) and role.
 * When the caller is ADMIN (US4/US10), each row also carries the role
 * actions (assign/revoke, blocked for the last admin) and the removal
 * dialog with its confirmation. Changing a role re-fetches the list so it
 * always reflects reality.
 */
export default function MemberListView({ groupId, currentUserId = null, isAdmin = false }) {
  const [members, setMembers] = useState(null)
  const [error, setError] = useState('')
  const [status, setStatus] = useState('')

  const apply = (result) => {
    if (result.ok) {
      setMembers(result.members)
      setError('')
    } else if (result.network) {
      setError(GROUPS_NETWORK_ERROR_MESSAGE)
    } else {
      setError(result.detail || 'No se pudo cargar la lista de miembros')
    }
  }

  // Consulta inicial (y re-consulta cuando cambia el grupo).
  useEffect(() => {
    let cancelled = false
    getMembers(groupId).then((result) => {
      if (cancelled) return
      apply(result)
    })
    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [groupId])

  /** Re-sync after a role change (and manual refreshes). */
  async function reload() {
    apply(await getMembers(groupId))
  }

  async function changeRole(member, action) {
    setStatus('')
    const result = action === 'grant'
      ? await grantAdminRole(groupId, member.userId)
      : await revokeAdminRole(groupId, member.userId)
    if (!result || result.network) {
      setError(GROUPS_NETWORK_ERROR_MESSAGE)
      return
    }
    if (!result.ok) {
      setError(result.detail || 'No se pudo cambiar el rol')
      return
    }
    setError('')
    setStatus(
      action === 'grant'
        ? `${member.name} ahora es administrador del grupo`
        : `${member.name} volvió a ser miembro del grupo`,
    )
    await reload() // the list refreshes role changes (per-group scope)
  }

  function roleLabel(role) {
    return role === 'ADMIN' ? 'Administrador' : 'Miembro'
  }

  if (error) {
    return <p role="alert" data-testid="members-error">{error}</p>
  }

  const adminIds = new Set(
    (members ?? []).filter((m) => m.role === 'ADMIN').map((m) => m.userId),
  )
  const isLastAdmin = adminIds.size <= 1

  return (
    <section data-testid="members-list">
      <h3>Miembros del grupo</h3>
      {status && <p role="status" data-testid="members-status">{status}</p>}
      {status === '' && isAdmin && isLastAdmin && adminIds.size === 1 && (
        <p role="alert" data-testid="members-last-admin-hint">
          {LAST_ADMIN}
        </p>
      )}
      {members === null && <p aria-busy="true">Cargando…</p>}
      <ul>
        {members?.map((member) => {
          const isSimulated = member.profileId != null
          const key = isSimulated ? `sim-${member.profileId}` : `user-${member.userId}`
          const isCurrentUser = !isSimulated && member.userId === currentUserId
          const isAdminTarget = member.role === 'ADMIN'
          const revokeable = isAdminTarget && adminIds.size > 1
          return (
            <li key={key} data-testid={isCurrentUser ? 'member-current' : `member-row-${key}`}>
              <img
                src={member.avatar || DEFAULT_AVATAR}
                alt=""
                width={36}
                height={36}
                data-testid={`member-avatar-${key}`}
              />
              <span className="member-name">{member.name}</span>
              {isCurrentUser && <span data-testid="member-you">Vos</span>}
              <span data-testid={`member-role-${key}`}>{roleLabel(member.role)}</span>
              {isAdmin && !isCurrentUser && !isSimulated && !isAdminTarget && (
                <button
                  type="button"
                  data-testid={`grant-admin-${key}`}
                  onClick={() => changeRole(member, 'grant')}
                >
                  Hacer admin
                </button>
              )}
              {isAdmin && !isCurrentUser && revokeable && (
                <button
                  type="button"
                  data-testid={`revoke-admin-${key}`}
                  onClick={() => changeRole(member, 'revoke')}
                >
                  Quitarle admin
                </button>
              )}
              {isAdmin && !isSimulated && (
                <RemoveMemberDialog
                  groupId={groupId}
                  member={{ userId: member.userId, name: member.name, role: member.role }}
                />
              )}
              {isAdmin && isSimulated && (
                <RemoveMemberDialog
                  groupId={groupId}
                  simulatedProfile={{ profileId: member.profileId, name: member.name }}
                />
              )}
            </li>
          )
        })}
      </ul>
    </section>
  )
}
