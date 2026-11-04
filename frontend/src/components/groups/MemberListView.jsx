import { useEffect, useRef, useState } from 'react'
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
 * dialog with its confirmation. Changing a role or removing someone re-fetches
 * the list and notifies `onChanged`, so the screen updates without a manual
 * refresh (fix 2026-11-04); changing `reloadKey` (e.g. after an alta) also
 * re-fetches. The backend identifies simulated profiles with
 * `simulatedProfileId` (GroupMemberView): they never get role actions.
 */
function simulatedIdOf(member) {
  return member?.simulatedProfileId ?? member?.profileId ?? null
}

export default function MemberListView({
  groupId,
  currentUserId = null,
  isAdmin = false,
  reloadKey = 0,
  onChanged,
}) {
  const [members, setMembers] = useState(null)
  const [error, setError] = useState('')
  const [status, setStatus] = useState('')
  // Only the latest request may update the list (no stale responses).
  const requestId = useRef(0)

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

  // Consulta inicial y re-consulta cuando cambia el grupo o el reloadKey.
  useEffect(() => {
    const current = ++requestId.current
    getMembers(groupId).then((result) => {
      if (current !== requestId.current) return
      apply(result)
    })
  }, [groupId, reloadKey])

  /** Re-sync after a role change or a removal. */
  async function reload() {
    const current = ++requestId.current
    const result = await getMembers(groupId)
    if (current === requestId.current) apply(result)
  }

  async function handleRemoved(name) {
    setStatus(`${name} ya no forma parte del grupo`)
    await reload()
    onChanged?.()
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
    onChanged?.()
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
          const simulatedId = simulatedIdOf(member)
          const isSimulated = simulatedId != null
          const key = isSimulated ? `sim-${simulatedId}` : `user-${member.userId}`
          const isCurrentUser = !isSimulated && member.userId === currentUserId
          const isAdminTarget = member.role === 'ADMIN'
          const revokeable = !isSimulated && isAdminTarget && adminIds.size > 1
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
                  onRemoved={() => handleRemoved(member.name)}
                />
              )}
              {isAdmin && isSimulated && (
                <RemoveMemberDialog
                  groupId={groupId}
                  simulatedProfile={{ profileId: simulatedId, name: member.name }}
                  onRemoved={() => handleRemoved(member.name)}
                />
              )}
            </li>
          )
        })}
      </ul>
    </section>
  )
}
