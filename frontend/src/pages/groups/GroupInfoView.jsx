import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { useAuth } from '../../auth/AuthContext'
import { getGroup, GROUPS_NETWORK_ERROR_MESSAGE } from '../../api/groupsClient'
import LeaveGroupPanel from '../../components/groups/LeaveGroupPanel'
import GroupDescriptionPanel from '../../components/groups/GroupDescriptionPanel'
import MemberListView from '../../components/groups/MemberListView'
import GroupPhotoPanel from '../../components/groups/GroupPhotoPanel'
import GroupPermissionsPanel from '../../components/groups/GroupPermissionsPanel'
import AddMemberView from './AddMemberView'
import GroupExpensesCard from '../../components/groups/GroupExpensesCard'

export default function GroupInfoView() {
  const { id } = useParams()
  const { user } = useAuth()
  const [group, setGroup] = useState(null)
  const [error, setError] = useState('')
  const [showAddMember, setShowAddMember] = useState(false)
  const [showPermissions, setShowPermissions] = useState(false)
  // Bumped after an alta so MemberListView re-fetches (fix 2026-11-04).
  const [membersVersion, setMembersVersion] = useState(0)

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

  // Re-reads the group (member count, role) after any change in the members.
  function refreshGroup() {
    getGroup(id).then((result) => {
      if (result.ok) setGroup(result.group)
    })
  }

  function handleMemberAdded() {
    setMembersVersion((version) => version + 1)
    refreshGroup()
  }

  function roleLabel(role) {
    return role === 'ADMIN' ? 'Administrador' : 'Miembro'
  }

  // UI anticipation only: the server always rules the last-admin motive.
  const isLastAdmin = group?.currentUserRole === 'ADMIN' && group?.memberCount === 1
  const isAdmin = group?.currentUserRole === 'ADMIN'

  return (
    <main className="page group-page">
      <nav className="group-page__back">
        <Link to="/groups">Volver a mis grupos</Link>
      </nav>
      {error && (
        <p role="alert" data-testid="group-error">
          {error}
        </p>
      )}
      {group && (
        <>
          {/* Encabezado: foto + nombre + rol del usuario en el grupo */}
          <header className="group-page__header" data-testid="group-detail">
            <div className="group-page__identity">
              <GroupPhotoPanel
                groupId={Number(id)}
                currentPhoto={group.photo ?? null}
                isAdmin={isAdmin}
                compact
              />
              <div>
                <h2>{group.name}</h2>
                <p data-testid="group-role">
                  Tu rol: {roleLabel(group.currentUserRole)} · {group.memberCount}{' '}
                  {group.memberCount === 1 ? 'miembro' : 'miembros'}
                </p>
              </div>
            </div>
            <GroupDescriptionPanel description={group.description ?? null} />
          </header>

          {/* Miembros activos: altas, bajas y cambios de rol se ven al instante */}
          <section className="group-card" aria-label="Miembros del grupo">
            <MemberListView
              groupId={Number(id)}
              currentUserId={user?.id ?? null}
              isAdmin={isAdmin}
              reloadKey={membersVersion}
              onChanged={refreshGroup}
            />
          </section>

          {/* Gastos del grupo (specs/017-debt-states, FR-021) */}
          <GroupExpensesCard groupId={id} group={group} />

          {/* Acciones del usuario en el grupo */}
          <section className="group-card" aria-label="Acciones del grupo">
            <div className="group-actions-row">
              <Link
                to={`/groups/${id}/history`}
                data-testid="group-history-link"
              >
                Ver historial de actividad
              </Link>
              <LeaveGroupPanel groupId={Number(id)} isLastAdmin={isLastAdmin} />
            </div>
          </section>

          {/* Controles exclusivos del administrador */}
          {isAdmin && (
            <section className="group-card" data-testid="admin-controls" aria-label="Administración">
              <h3>Administración</h3>
              <div className="group-admin-controls">
                <button
                  type="button"
                  onClick={() => { setShowAddMember(!showAddMember) }}
                >
                  {showAddMember ? 'Cerrar' : 'Agregar miembro'}
                </button>
                <button
                  type="button"
                  onClick={() => { setShowPermissions(!showPermissions) }}
                >
                  {showPermissions ? 'Cerrar ajustes' : 'Configuración del grupo'}
                </button>
              </div>
              {showAddMember && (
                <div className="group-admin-panel" data-testid="add-member-panel">
                  <AddMemberView
                    group={group}
                    permissions={group.permissions ?? {}}
                    onAdded={handleMemberAdded}
                  />
                </div>
              )}
              {showPermissions && (
                <div className="group-admin-panel" data-testid="permissions-panel">
                  <GroupPermissionsPanel group={group} permissions={group.permissions ?? {}} />
                </div>
              )}
            </section>
          )}
        </>
      )}
    </main>
  )
}
