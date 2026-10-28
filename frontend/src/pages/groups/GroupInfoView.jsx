import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { useAuth } from '../../auth/AuthContext'
import { getGroup, GROUPS_NETWORK_ERROR_MESSAGE } from '../../api/groupsClient'
import LeaveGroupPanel from '../../components/groups/LeaveGroupPanel'
import GroupDescriptionPanel from '../../components/groups/GroupDescriptionPanel'
import MemberListView from '../../components/groups/MemberListView'
import GroupPhotoPanel from '../../components/groups/GroupPhotoPanel'

export default function GroupInfoView() {
  const { id } = useParams()
  const { user } = useAuth()
  const [group, setGroup] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    getGroup(id).then((result) => {
      if (cancelled) return
      if (result.ok) {
        setGroup(result.group)
      } else if (!result.network) {
        setError('No existe el grupo solicitado o no sos miembro')
      } else {
        setError(GROUPS_NETWORK_ERROR_MESSAGE)
      }
    })
    return () => {
      cancelled = true
    }
  }, [id])

    function roleLabel(role) {
    return role === 'ADMIN' ? 'Administrador' : 'Miembro'
  }

  // UI anticipation only: the server always rules the last-admin motive.
  const isLastAdmin = group?.currentUserRole === 'ADMIN' && group?.memberCount === 1

  return (
    <main className="page">
      <h1>Información del grupo</h1>
      <Link to="/groups">Volver a mis grupos</Link>
      {error && (
        <p role="alert" data-testid="group-error">
          {error}
        </p>
      )}
      {group && (
        <section data-testid="group-detail">
          <h2>{group.name}</h2>
          {group.photo && (
            <img
              src={group.photo}
              alt="Foto del grupo"
              width={96}
              height={96}
              data-testid="group-photo-main"
            />
          )}
          <GroupPhotoPanel groupId={Number(id)} currentPhoto={group.photo ?? null} isAdmin={group.currentUserRole === 'ADMIN'} />
          <GroupDescriptionPanel description={group.description ?? null} />
          <p data-testid="group-role">
            Tu rol: {roleLabel(group.currentUserRole)} · {group.memberCount}{' '}
            {group.memberCount === 1 ? 'miembro' : 'miembros'}
          </p>
          <Link to={`/groups/${id}/history`}>Ver historial de actividad</Link>
          <LeaveGroupPanel groupId={Number(id)} isLastAdmin={isLastAdmin} />
          <MemberListView groupId={Number(id)} currentUserId={user?.id ?? null} />
        </section>
      )}
    </main>
  )
}
