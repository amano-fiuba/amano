import { useEffect, useState } from 'react'
import { Link, useLocation } from 'react-router'
import { listGroups, GROUPS_NETWORK_ERROR_MESSAGE } from '../../api/groupsClient'
import { byLatestActivity, formatActivityDate, memberCountLabel } from './groupDisplay'
import GroupRowsSkeleton from '../../components/groups/GroupRowsSkeleton'
import './groups.scss'

function groupCountLabel(count) {
  return count === 1 ? '1 grupo' : `${count} grupos`
}

function roleLabel(role) {
  return role === 'ADMIN' ? 'Administrador' : 'Miembro'
}

export default function GroupListView() {
  const location = useLocation()
  // Un usuario recién verificado llega desde /verify-email con state
  // { verifiedWelcome: true }: más acá que al dashboard, porque el primer paso
  // de la app es unirse o crear un grupo. El estado se limpia al recargar.
  const verifiedWelcome = location.state?.verifiedWelcome === true
  const [groups, setGroups] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    listGroups().then((result) => {
      if (cancelled) return
      if (result.ok) {
        setGroups(byLatestActivity(result.groups))
      } else {
        setError(GROUPS_NETWORK_ERROR_MESSAGE)
      }
    })
    return () => {
      cancelled = true
    }
  }, [])

  const isEmpty = groups?.length === 0

  return (
    <main className="page groups-page">
      <header className="groups-page__header">
        <div>
          <h1>Mis grupos</h1>
          {groups?.length > 0 && (
            <p className="groups-page__count" data-testid="groups-count">
              {groupCountLabel(groups.length)}
            </p>
          )}
        </div>
        {!isEmpty && (
          <Link className="buttonLink" to="/groups/new">
            Crear nuevo grupo
          </Link>
        )}
      </header>
      {verifiedWelcome && (
        <p role="status" data-testid="groups-verified-welcome">
          Cuenta verificada, ¡bienvenido! Creá tu primer grupo o esperá a que te
          agreguen a uno.
        </p>
      )}
      {error && (
        <p role="alert" data-testid="groups-error">
          {error}
        </p>
      )}
      {groups === null && !error && <GroupRowsSkeleton />}
      {isEmpty && (
        <div className="groups-page__empty" data-testid="groups-empty">
          <p>Todavía no sos miembro de ningún grupo</p>
          <Link className="buttonLink" to="/groups/new">
            Crear mi primer grupo
          </Link>
        </div>
      )}
      {groups?.length > 0 && (
        <ul className="group-rows" data-testid="groups-list">
          {groups.map((group) => (
            <li key={group.id}>
              <Link className="group-row" to={`/groups/${group.id}`}>
                {group.photo ? (
                  <img className="group-row__mark" src={group.photo} alt="" width={48} height={48} />
                ) : (
                  <span
                    className="group-row__mark"
                    data-testid="group-avatar-default"
                    aria-hidden="true"
                  >
                    {group.name.charAt(0).toUpperCase()}
                  </span>
                )}
                <span className="group-row__body">
                  <span className="group-row__title">
                    <strong>{group.name}</strong>
                    <span
                      className={`role-chip role-chip--${group.currentUserRole.toLowerCase()}`}
                      data-testid={`group-role-${group.id}`}
                    >
                      {roleLabel(group.currentUserRole)}
                    </span>
                  </span>
                  {group.description && (
                    <span className="group-row__description">{group.description}</span>
                  )}
                  <small>
                    {memberCountLabel(group.memberCount)}
                    {' · '}Actividad: {formatActivityDate(group.lastActivityAt)}
                  </small>
                </span>
                <span className="group-row__chevron" aria-hidden="true">›</span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </main>
  )
}
