import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { useAuth } from '../../auth/AuthContext'
import { getGroup, GROUPS_NETWORK_ERROR_MESSAGE } from '../../api/groupsClient'
import LeaveGroupPanel from '../../components/groups/LeaveGroupPanel'
import GroupDescriptionPanel from '../../components/groups/GroupDescriptionPanel'
import MemberListView from '../../components/groups/MemberListView'
import GroupPhotoPanel from '../../components/groups/GroupPhotoPanel'
import GroupEditForm from '../../components/groups/GroupEditForm'
import { GroupSettingsIcon, HistoryIcon, PencilIcon } from '../../components/NavIcons'
import GroupPermissionsPanel from '../../components/groups/GroupPermissionsPanel'
import GroupRowsSkeleton from '../../components/groups/GroupRowsSkeleton'
import AddMemberView from './AddMemberView'
import GroupExpensesCard from '../../components/groups/GroupExpensesCard'
import GroupFeed from '../../components/groups/GroupFeed'
import { memberCountLabel } from './groupDisplay'
import './groups.scss'

function roleLabel(role) {
  return role === 'ADMIN' ? 'Administrador' : 'Miembro'
}

export default function GroupInfoView() {
  const { id } = useParams()
  const { user } = useAuth()
  const [group, setGroup] = useState(null)
  const [error, setError] = useState('')
  const [showAddMember, setShowAddMember] = useState(false)
  const [showPermissions, setShowPermissions] = useState(false)
  const [editing, setEditing] = useState(false)
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

  const isAdmin = group?.currentUserRole === 'ADMIN'
  const canEditGroupData = isAdmin || Boolean(group?.permissions?.membersCanEditGroupData)

  return (
    <main className="group-detail">
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
              {editing ? (
                <GroupEditForm
                  group={group}
                  onCancel={() => setEditing(false)}
                  onSaved={(updated) => {
                    setGroup(updated)
                    setEditing(false)
                  }}
                />
              ) : (
                <>
                  <GroupPhotoPanel photo={group.photo ?? null} />
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
                </>
              )}
            </div>
            {/* Acciones arriba a la derecha y "Salir" en la esquina inferior derecha. */}
            <div className="group-hero__side">
            <div className="group-hero__actions">
              <Link
                className="group-action"
                to={`/groups/${id}/history`}
                data-testid="group-history-link"
              >
                <HistoryIcon /> Historial de actividad
              </Link>
              {canEditGroupData && !editing && (
                <button
                  type="button"
                  className="group-action"
                  data-testid="group-edit-button"
                  onClick={() => setEditing(true)}
                >
                  <PencilIcon /> Editar
                </button>
              )}
              {isAdmin && (
                <button
                  type="button"
                  className="group-action"
                  aria-expanded={showPermissions}
                  aria-label={showPermissions ? 'Cerrar configuración' : undefined}
                  onClick={() => { setShowPermissions(!showPermissions) }}
                >
                  {showPermissions ? 'Cerrar' : <><GroupSettingsIcon /> Configuración</>}
                </button>
              )}
            </div>
            <LeaveGroupPanel
              groupId={Number(id)}
              isLastAdmin={Boolean(group.isLastAdmin)}
              isLastMember={Boolean(group.isLastMember)}
            />
            </div>
            {showPermissions && (
              <div className="group-admin-panel" data-testid="permissions-panel">
                <GroupPermissionsPanel
                  group={group}
                  permissions={group.permissions ?? {}}
                  onSaved={() => {
                    setShowPermissions(false)
                    refreshGroup()
                  }}
                />
              </div>
            )}
          </header>

          {/* Miembros, gastos y actividad a la vez, como la pantalla principal. */}
          <div className="group-board">
          <section className="group-detail__section" aria-labelledby="group-members-title">
            <div className="group-detail__section-header">
              <h2 id="group-members-title">Miembros ({group.memberCount})</h2>
              {isAdmin && (
                <button
                  type="button"
                  className="group-action"
                  aria-haspopup="dialog"
                  onClick={() => setShowAddMember(true)}
                >
                  Agregar miembro
                </button>
              )}
            </div>
            <MemberListView
              groupId={Number(id)}
              currentUserId={user?.id ?? null}
              isAdmin={isAdmin}
              reloadKey={membersVersion}
              onChanged={refreshGroup}
            />
          </section>

          {/* Gastos del grupo (specs/017-debt-states, FR-021) */}
          <GroupExpensesCard groupId={id} group={group} reloadKey={membersVersion} />

          <section className="group-detail__section" aria-labelledby="group-activity-title">
            <div className="group-detail__section-header">
              <h2 id="group-activity-title">Actividad</h2>
              <Link to={`/groups/${id}/history`}>Ver todo</Link>
            </div>
            <GroupFeed
              groupId={id}
              size={8}
              emptyMessage="Todavía no hay actividad"
              reloadKey={membersVersion}
            />
          </section>
          </div>

          {showAddMember && (
            <AddMemberView
              group={group}
              permissions={group.permissions ?? {}}
              onAdded={handleMemberAdded}
              onClose={() => setShowAddMember(false)}
            />
          )}
        </>
      )}
    </main>
  )
}
