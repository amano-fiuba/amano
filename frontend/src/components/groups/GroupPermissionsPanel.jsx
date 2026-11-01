import { useState } from 'react'
import { updateGroup, GROUPS_NETWORK_ERROR_MESSAGE } from '../../api/groupsClient'

const CHECKBOXES = [
  { key: 'membersCannotMakeExpenses', label: 'No podrán hacer gastos' },
  { key: 'membersCannotInvite', label: 'No podrán invitar gente' },
  { key: 'membersCannotEditSettings', label: 'No podrán editar ajustes del grupo' },
  { key: 'membersCanManageSimulatedProfiles', label: 'Pueden administrar perfiles simulados' },
  { key: 'membersCanGenerateExpensesForOthers', label: 'Pueden generar gastos en nombre de otras personas' },
  { key: 'paymentsRequireValidation', label: 'Los pagos requieren validación' },
]

export default function GroupPermissionsPanel({ group, permissions }) {
  const [values, setValues] = useState({ ...permissions })
  const [saving, setSaving] = useState(false)
  const [confirmation, setConfirmation] = useState('')
  const [error, setError] = useState('')

  function toggle(key) {
    setValues((current) => ({ ...current, [key]: !current[key] }))
  }

  async function handleSubmit() {
    setSaving(true)
    setConfirmation('')
    setError('')
    const result = await updateGroup(group.id, { permissions: values })
    if (result.ok) {
      setValues({ ...values, ...(result.group?.permissions ?? {}) })
      setConfirmation('Permisos actualizados')
    } else if (result.network) {
      setError(GROUPS_NETWORK_ERROR_MESSAGE)
    } else {
      setError(result.detail || 'No se pudieron guardar los permisos')
    }
    setSaving(false)
  }

  return (
    <section data-testid="permissions-panel">
      <h2>Permisos de los miembros</h2>
      {CHECKBOXES.map(({ key, label }) => (
        <div key={key}>
          <label>
            <input
              type="checkbox"
              checked={Boolean(values[key])}
              onChange={() => toggle(key)}
            />{' '}
            {label}
          </label>
        </div>
      ))}
      <button type="button" onClick={handleSubmit} disabled={saving}>
        Guardar permisos
      </button>
      {confirmation && <p role="status">{confirmation}</p>}
      {error && (
        <p role="alert" data-testid="permissions-error">
          {error}
        </p>
      )}
    </section>
  )
}
