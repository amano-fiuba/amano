import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { listGroups, GROUPS_NETWORK_ERROR_MESSAGE } from '../../api/groupsClient'

export default function GroupListView() {
  const [groups, setGroups] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    listGroups().then((result) => {
      if (cancelled) return
      if (result.ok) {
        setGroups(result.groups)
      } else {
        setError(GROUPS_NETWORK_ERROR_MESSAGE)
        setGroups([])
      }
    })
    return () => {
      cancelled = true
    }
  }, [])

  function roleLabel(role) {
    return role === 'ADMIN' ? 'Administrador' : 'Miembro'
  }

  return (
    <main className="page">
      <h1>Mis grupos</h1>
      <Link to="/groups/new">Crear nuevo grupo</Link>
      {error && (
        <p role="alert" data-testid="groups-error">
          {error}
        </p>
      )}
      {groups === null ? (
        <p aria-busy="true">Cargando…</p>
      ) : groups.length === 0 ? (
        <p data-testid="groups-empty">Todavía no sos miembro de ningún grupo</p>
      ) : (
        <ul data-testid="groups-list">
          {groups.map((group) => (
            <li key={group.id}>
              <Link to={`/groups/${group.id}`}>
                {group.photo ? (
                  <img src={group.photo} alt="" width={36} height={36} />
                ) : (
                  <span data-testid="group-avatar-default" aria-hidden="true" />
                )}
                <span>{group.name}</span>
              </Link>
              <span data-testid={`group-role-${group.id}`}>{roleLabel(group.currentUserRole)}</span>
            </li>
          ))}
        </ul>
      )}
    </main>
  )
}
