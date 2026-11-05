import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { useAuth } from '../../auth/AuthContext'
import { getGroup, GROUPS_NETWORK_ERROR_MESSAGE } from '../../api/groupsClient'
import LeaveGroupPanel from '../../components/groups/LeaveGroupPanel'
import GroupDescriptionPanel from '../../components/groups/GroupDescriptionPanel'
import MemberListView from '../../components/groups/MemberListView'
import GroupPhotoPanel from '../../components/groups/GroupPhotoPanel'
import GroupPermissionsPanel from '../../components/groups/GroupPermissionsPanel'
import GroupRowsSkeleton from '../../components/groups/GroupRowsSkeleton'
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

  // UI anticipation only: the server always rules the last-admin motive.
  const isLastAdmin = group?.currentUserRole === 'ADMIN' && group?.memberCount === 1
  const isAdmin = group?.currentUserRole === 'ADMIN'

  return (
    <main className="page group-page group-detail">
      <nav className="group-page__back">
        <Link to="/groups">
          <span aria-hidden="true">←</span> Mis grupos
        </Link>
      </nav>
      {error && (
        <p role="alert" data-testid="group-error">
          {error}
        </p>
      )}
      {!group && !error && <GroupRowsSkeleton rows={2} />}
      {group && (
        <>
          <header className="group-hero" data-testid="group-detail">
            <div className="group-hero__identity">
              <GroupPhotoPanel
                groupId={Number(id)}
                currentPhoto={group.photo ?? null}
                isAdmin={isAdmin}
              />
              <div className="group-hero__text">
                <h1>{group.name}</h1>
                <GroupDescriptionPanel description={group.description ?? null} />
                <p className="group-hero__meta" data-testid="group-role">
                  {memberCountLabel(group.memberCount)}
                  <span aria-hidden="true">·</span>
                  <span className={`role-chip role-chip--${group.currentUserRole.toLowerCase()}`}>
                    <span className="visually-hidden">Tu rol: </span>
                    {roleLabel(group.currentUserRole)}
                  </span>
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
                className="group-action"
                to={`/groups/${id}/history`}
                data-testid="group-history-link"
              >
                Historial de actividad
              </Link>
              {isAdmin && (
                <button
                  type="button"
                  className="group-action"
                  aria-expanded={showPermissions}
                  onClick={() => { setShowPermissions(!showPermissions) }}
                >
                  {showPermissions ? 'Cerrar ajustes' : 'Configuración del grupo'}
                </button>
              )}
            </div>
            {showPermissions && (
              <div className="group-admin-panel" data-testid="permissions-panel">
                <GroupPermissionsPanel group={group} permissions={group.permissions ?? {}} />
              </div>
            )}
          </header>

          <section className="group-detail__section" aria-labelledby="group-members-title">
            <div className="group-detail__section-header">
              <h2 id="group-members-title">Miembros ({group.memberCount})</h2>
              {isAdmin && (
                <button
                  type="button"
                  className="group-action"
                  aria-expanded={showAddMember}
                  onClick={() => { setShowAddMember(!showAddMember) }}
                >
                  {showAddMember ? 'Cerrar' : 'Agregar miembro'}
                </button>
              )}
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
            <MemberListView
              groupId={Number(id)}
              currentUserId={user?.id ?? null}
              isAdmin={isAdmin}
              reloadKey={membersVersion}
              onChanged={refreshGroup}
            />
          </section>

          <footer className="group-detail__footer">
            <LeaveGroupPanel groupId={Number(id)} isLastAdmin={isLastAdmin} />
          </footer>
        </>
      )}
    </main>
  )
}
