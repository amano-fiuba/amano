import { useEffect, useState } from 'react'
import { getMembers, GROUPS_NETWORK_ERROR_MESSAGE } from '../../api/groupsClient'

const DEFAULT_AVATAR = '/avatar-default.png'

function fullName(member) {
  return member.name
}

/**
 * Members list (US6, T079): every ACTIVE member — registered users and
 * simulated profiles — with name, avatar (the user-avatar mechanism: NULL
 * avatar → gray default, same as UsersSearchPage/DashboardPage) and the
 * per-group role. The current user is visually distinguished ("Vos").
 * The list re-fetches whenever groupId changes (add/leave/removal flows come
 * back to this view, which then shows the current state).
 */
export default function MemberListView({ groupId, currentUserId = null }) {
  const [members, setMembers] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    getMembers(groupId).then((result) => {
      if (cancelled) return
      if (result.ok) {
        setMembers(result.members)
        setError('')
      } else if (result.network) {
        setError(GROUPS_NETWORK_ERROR_MESSAGE)
      } else {
        setError(result.detail || 'No se pudo cargar la lista de miembros')
      }
    })
    return () => {
      cancelled = true
    }
  }, [groupId])

  function roleLabel(role) {
    return role === 'ADMIN' ? 'Administrador' : 'Miembro'
  }

  if (error) {
    return <p role="alert" data-testid="members-error">{error}</p>
  }

  return (
    <section data-testid="members-list">
      <h3>Miembros del grupo</h3>
      {members === null && <p aria-busy="true">Cargando…</p>}
      <ul>
        {members?.map((member) => {
          const isSimulated = member.profileId != null
          const isCurrentUser = !isSimulated && member.userId === currentUserId
          return (
            <li
              key={isSimulated ? `sim-${member.profileId}` : `user-${member.userId}`}
              data-testid={isCurrentUser ? 'member-current' : `member-${member.userId ?? member.profileId}`}
              className={isCurrentUser ? 'member-row member-row--current' : 'member-row'}
            >
              <img
                src={member.avatar || DEFAULT_AVATAR}
                alt=""
                width={36}
                height={36}
                data-testid={`member-avatar-${member.userId ?? member.profileId}`}
              />
              <span className="member-name">{fullName(member)}</span>
              {isCurrentUser && <span data-testid="member-you">Vos</span>}
              <span data-testid={`member-role-${member.userId ?? member.profileId}`}>
                {roleLabel(member.role)}
              </span>
            </li>
          )
        })}
      </ul>
    </section>
  )
}
