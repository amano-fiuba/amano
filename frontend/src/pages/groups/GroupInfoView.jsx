import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { getGroup, GROUPS_NETWORK_ERROR_MESSAGE } from '../../api/groupsClient'
import LeaveGroupPanel from '../../components/groups/LeaveGroupPanel'

export default function GroupInfoView() {
  const { id } = useParams()
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
          {group.description && <p>{group.description}</p>}
          <p data-testid="group-role">
            Tu rol: {roleLabel(group.currentUserRole)} · {group.memberCount}{' '}
            {group.memberCount === 1 ? 'miembro' : 'miembros'}
          </p>
          <Link to={`/groups/${id}/history`}>Ver historial de actividad</Link>
          <LeaveGroupPanel groupId={Number(id)} isLastAdmin={isLastAdmin} />
        </section>
      )}
    </main>
  )
}
