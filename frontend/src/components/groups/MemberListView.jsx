import { useEffect, useRef, useState } from 'react'
import {
  getMembers,
  grantAdminRole,
  revokeAdminRole,
  GROUPS_NETWORK_ERROR_MESSAGE,
} from '../../api/groupsClient'
import GroupRowsSkeleton from './GroupRowsSkeleton'
import MemberActionsMenu from './MemberActionsMenu'
import MemberBalance from './MemberBalance'
import RemoveMemberDialog from './RemoveMemberDialog'
import { useSnackbar } from '../Snackbar'

const DEFAULT_AVATAR = '/avatar-default.png'
const LAST_ADMIN = 'Sos el último administrador: asigná otro administrador antes de salir'

/**
 * Members list (US6, T079): every ACTIVE member — registered users and
 * simulated profiles — with name, avatar (NULL → gray default) and role.
 * The caller comes first. When they are ADMIN (US4/US10), every other row
 * carries a menu with the role actions (assign/revoke, blocked for the last
 * admin) and the removal dialog with its confirmation. Changing a role or removing someone re-fetches
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
  const showSnackbar = useSnackbar()
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

  async function handleRemoved() {
    await reload()
    onChanged?.()
  }

  async function changeRole(member, action) {
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
    showSnackbar(
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

  const isCurrentUser = (member) => member.userId != null && member.userId === currentUserId
  const ordered = [...(members ?? [])].sort(
    (a, b) => Number(isCurrentUser(b)) - Number(isCurrentUser(a)),
  )

  return (
    <section data-testid="members-list">
      {isAdmin && isLastAdmin && adminIds.size === 1 && (
        <p role="alert" className="member-rows__hint" data-testid="members-last-admin-hint">
          {LAST_ADMIN}
        </p>
      )}
      {members === null && <GroupRowsSkeleton rows={2} />}
      <ul className="member-rows">
        {ordered.map((member) => {
          const simulatedId = simulatedIdOf(member)
          const isSimulated = simulatedId != null
          const key = isSimulated ? `sim-${simulatedId}` : `user-${member.userId}`
          const isYou = isCurrentUser(member)
          const isAdminTarget = member.role === 'ADMIN'
          const revokeable = !isSimulated && isAdminTarget && adminIds.size > 1
          return (
            <li
              key={key}
              className="member-row"
              data-testid={isYou ? 'member-current' : `member-row-${key}`}
            >
              <img
                className="member-row__avatar"
                src={member.avatar || DEFAULT_AVATAR}
                alt=""
                width={40}
                height={40}
                data-testid={`member-avatar-${key}`}
              />
              <span className="member-row__body">
                <span className="member-name">{member.name}</span>
                <span className="member-row__chips">
                  {isYou && (
                    <span className="role-chip role-chip--you" data-testid="member-you">Yo</span>
                  )}
                  <span
                    className={`role-chip role-chip--${member.role.toLowerCase()}`}
                    data-testid={`member-role-${key}`}
                  >
                    {roleLabel(member.role)}
                  </span>
                </span>
                {!isYou && <MemberBalance balances={member.balances} payUrl={member.payUrl} />}
              </span>
              {isAdmin && !isYou && (
                <MemberActionsMenu memberName={member.name} testId={`member-actions-${key}`}>
                  {(close) => (
                    <>
                      {!isSimulated && !isAdminTarget && (
                        <button
                          type="button"
                          className="member-menu__item"
                          data-testid={`grant-admin-${key}`}
                          onClick={() => {
                            close()
                            changeRole(member, 'grant')
                          }}
                        >
                          Hacer admin
                        </button>
                      )}
                      {revokeable && (
                        <button
                          type="button"
                          className="member-menu__item"
                          data-testid={`revoke-admin-${key}`}
                          onClick={() => {
                            close()
                            changeRole(member, 'revoke')
                          }}
                        >
                          Quitarle admin
                        </button>
                      )}
                      <RemoveMemberDialog
                        groupId={groupId}
                        member={isSimulated ? undefined : { userId: member.userId, name: member.name, role: member.role }}
                        simulatedProfile={isSimulated ? { profileId: simulatedId, name: member.name } : undefined}
                        onRemoved={handleRemoved}
                      />
                    </>
                  )}
                </MemberActionsMenu>
              )}
            </li>
          )
        })}
      </ul>
    </section>
  )
}
