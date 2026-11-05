import { useState } from 'react'
import { createGroup } from '../../api/groupsClient'

import { useSnackbar } from '../../components/Snackbar'
import SubmitButton from '../../components/SubmitButton'
import TextField from '../../components/TextField'

// Exact messages of the UI contract; exported for the tests.
export const NAME_REQUIRED_MESSAGE = 'El nombre del grupo es obligatorio'
export const MEMBER_REQUIRED_MESSAGE =
  'Agregá al menos un miembro adicional (amigo registrado o perfil simulado)'

const MEMBER_TYPES = { REGISTERED: 'REGISTERED', SIMULATED: 'SIMULATED' }

function firstNameOf(group) {
  return group?.name || 'Grupo'
}

/**
 * Create-group screen (T031, FR-001): name (required), optional description
 * and the mandatory additional member — a registered friend (email) or a
 * simulated profile (name + email). Errors come inline; success announces the
 * created group. Placeholder: once groups exist, this flows to /groups/:id.
 */
export default function CreateGroupView() {
  const [memberTab, setMemberTab] = useState(MEMBER_TYPES.REGISTERED)
  const showSnackbar = useSnackbar()
  const [saving, setSaving] = useState(false)

  const [fields, setFields] = useState({ name: '', description: '' })
  const [errors, setErrors] = useState({})

  const [friend, setFriend] = useState({ identifier: '' })

  const [simulated, setSimulated] = useState({ name: '', email: '' })

  function findTabError() {
    if (memberTab === MEMBER_TYPES.SIMULATED) {
      if (!simulated.name.trim() || !simulated.email.trim()) {
        return { member: 'Ingresá nombre y correo del perfil simulado' }
      }
      return {}
    }
    if (!friend.identifier.trim()) {
      return { member: 'Ingresá el correo del amigo' }
    }
    return {}
  }

  function asPayload() {
    if (memberTab === MEMBER_TYPES.SIMULATED) {
      return {
        memberType: 'SIMULATED',
        identifier: simulated.email.trim(),
        name: simulated.name.trim(),
      }
    }
    return { memberType: 'REGISTERED', identifier: friend.identifier.trim() }
  }

  async function handleSubmit(event) {
    event.preventDefault()
    const nextErrors = findTabError()
    setErrors(nextErrors)
    if (!fields.name.trim() || Object.keys(nextErrors).length > 0) {
      setErrors((prev) => ({
        ...prev,
        ...(!fields.name.trim()
          ? { name: NAME_REQUIRED_MESSAGE, ...nextErrors }
          : nextErrors),
      }))
      return
    }
    setSaving(true)
    const result = await createGroup({
      name: fields.name.trim(),
      description: fields.description.trim(),
      member: asPayload(),
    })
    setSaving(false)
    if (result.ok) {
      showSnackbar(`Grupo creado: ${firstNameOf(result.group)}`)
      setErrors({})
    } else {
      setErrors(result.fields || findTabError())
    }
  }

  return (
    <main className="page">
      <h1>Crear nuevo grupo</h1>
      <form onSubmit={handleSubmit} noValidate>
        <TextField
          id="group-name"
          label="Nombre del grupo"
          value={fields.name}
          onChange={(event) => setFields((prev) => ({ ...prev, name: event.target.value }))}
          error={errors.name}
        />
        <TextField
          id="group-description"
          label="Descripción (opcional)"
          value={fields.description}
          onChange={(event) => setFields((prev) => ({ ...prev, description: event.target.value }))}
          error={errors.description}
        />

        <fieldset>
          <legend>Miembro adicional</legend>
          <label>
            <input
              type="radio"
              name="memberType"
              checked={memberTab === MEMBER_TYPES.REGISTERED}
              onChange={() => setMemberTab(MEMBER_TYPES.REGISTERED)}
            />
            Amigo registrado
          </label>
          {memberTab === MEMBER_TYPES.REGISTERED && (
            <TextField
              id="friend-email"
              label="Correo del amigo"
              type="email"
              value={friend.identifier}
              onChange={(event) => setFriend({ identifier: event.target.value })}
              error={memberTab === MEMBER_TYPES.REGISTERED ? errors.member : undefined}
            />
          )}
          <label>
            <input
              type="radio"
              name="memberType"
              checked={memberTab === MEMBER_TYPES.SIMULATED}
              onChange={() => setMemberTab(MEMBER_TYPES.SIMULATED)}
            />
            Perfil simulado
          </label>
          {memberTab === MEMBER_TYPES.SIMULATED && (
            <>
              <TextField
                id="simulated-name"
                label="Nombre del perfil simulado"
                value={simulated.name}
                onChange={(event) => setSimulated((prev) => ({ ...prev, name: event.target.value }))}
                error={memberTab === MEMBER_TYPES.SIMULATED ? errors.name : undefined}
              />
              <TextField
                id="simulated-email"
                label="Correo del perfil simulado"
                type="email"
                value={simulated.email}
                onChange={(event) => setSimulated((prev) => ({ ...prev, email: event.target.value }))}
                error={memberTab === MEMBER_TYPES.SIMULATED ? errors.identifier : undefined}
              />
              {errors.member && (
                <p role="alert" data-testid="member-error">
                  {errors.member}
                </p>
              )}
            </>
          )}
        </fieldset>

        <SubmitButton loading={saving}>Crear grupo</SubmitButton>
        <p>{MEMBER_REQUIRED_MESSAGE}</p>
      </form>
    </main>
  )
}
