import { useEffect, useState } from 'react'
import {
  deleteGroupPhoto,
  getGroup,
  updateGroup,
  uploadGroupPhoto,
  GROUPS_NETWORK_ERROR_MESSAGE,
} from '../../api/groupsClient'
import GroupPhotoPanel from './GroupPhotoPanel'
import { useSnackbar } from '../Snackbar'

const FORMAT_MESSAGE = 'El archivo debe ser una imagen JPG o PNG válida'
// 022 T078: same limits and text as the backend (GroupService).
export const MAX_DEBT_DUE_DAYS = 3650
export const DEBT_DUE_DAYS_INVALID =
  `Los días para el vencimiento tienen que ser un número entero entre 0 y ${MAX_DEBT_DUE_DAYS}`

/** '' → null (never overdue); '15' → 15; anything else → undefined (invalid). */
function parseDueDays(raw) {
  const text = String(raw ?? '').trim()
  if (text === '') return null
  if (!/^\d+$/.test(text)) return undefined
  const days = Number(text)
  return days <= MAX_DEBT_DUE_DAYS ? days : undefined
}

/**
 * Edit mode of the group data (photo, name, description). Changes are staged
 * locally and persisted together on "Guardar cambios"; "Cancelar" discards.
 */
export default function GroupEditForm({ group, onSaved, onCancel }) {
  const showSnackbar = useSnackbar()
  const [name, setName] = useState(group.name)
  const [description, setDescription] = useState(group.description ?? '')
  const isAdmin = group.currentUserRole === 'ADMIN'
  const [dueDays, setDueDays] = useState(group.debtDueDays == null ? '' : String(group.debtDueDays))
  const [dueDaysError, setDueDaysError] = useState('')
  const [newFile, setNewFile] = useState(null)
  const [previewUrl, setPreviewUrl] = useState(null)
  const [removePhoto, setRemovePhoto] = useState(false)
  const [photoError, setPhotoError] = useState('')
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  useEffect(() => () => {
    if (previewUrl) URL.revokeObjectURL?.(previewUrl)
  }, [previewUrl])

  function handlePick(file) {
    if (file.type !== 'image/jpeg' && file.type !== 'image/png') {
      setPhotoError(FORMAT_MESSAGE)
      return
    }
    setPhotoError('')
    setNewFile(file)
    setRemovePhoto(false)
    setPreviewUrl(URL.createObjectURL ? URL.createObjectURL(file) : null)
  }

  function handleRemove() {
    setPhotoError('')
    setNewFile(null)
    setPreviewUrl(null)
    setRemovePhoto(true)
  }

  const shownPhoto = previewUrl || (removePhoto ? null : group.photo ?? null)

  async function handleSubmit(event) {
    event.preventDefault()
    if (!name.trim()) {
      setError('El nombre del grupo es obligatorio')
      return
    }
    const days = parseDueDays(dueDays)
    if (isAdmin && days === undefined) {
      setDueDaysError(DEBT_DUE_DAYS_INVALID)
      return
    }
    setDueDaysError('')
    setSaving(true)
    setError('')

    const payload = {}
    if (name.trim() !== group.name) payload.name = name
    if (description !== (group.description ?? '')) payload.description = description
    if (isAdmin && days !== (group.debtDueDays ?? null)) payload.debtDueDays = days
    if (Object.keys(payload).length) {
      const result = await updateGroup(group.id, payload)
      if (!result.ok) {
        const dueDaysMessage = result.fields?.debtDueDays
        if (dueDaysMessage) setDueDaysError(dueDaysMessage)
        setError(result.network
          ? GROUPS_NETWORK_ERROR_MESSAGE
          : result.fields?.name
            || (dueDaysMessage ? '' : result.detail || 'No se pudieron guardar los cambios'))
        setSaving(false)
        return
      }
    }

    if (newFile || removePhoto) {
      const result = newFile
        ? await uploadGroupPhoto(group.id, newFile)
        : await deleteGroupPhoto(group.id)
      if (!result || !result.ok) {
        setError(!result || result.network
          ? GROUPS_NETWORK_ERROR_MESSAGE
          : result.detail || 'No se pudo actualizar la foto del grupo')
        setSaving(false)
        return
      }
    }

    const fresh = await getGroup(group.id)
    setSaving(false)
    if (fresh.ok) {
      showSnackbar('Los datos del grupo se actualizaron')
      onSaved(fresh.group)
    } else {
      setError(GROUPS_NETWORK_ERROR_MESSAGE)
    }
  }

  return (
    <form className="group-edit-form" onSubmit={handleSubmit} data-testid="group-edit-form">
      <GroupPhotoPanel
        photo={shownPhoto}
        editing
        onPick={handlePick}
        onRemove={handleRemove}
        error={photoError}
      />
      <label>
        Nombre
        <input value={name} maxLength={100} onChange={(e) => setName(e.target.value)} />
      </label>
      <label>
        Descripción
        <textarea value={description} rows={3} onChange={(e) => setDescription(e.target.value)} />
      </label>
      {isAdmin && (
        <div className="field">
          <label htmlFor="group-due-days">Días para el vencimiento de las deudas</label>
          <input
            id="group-due-days"
            inputMode="numeric"
            autoComplete="off"
            value={dueDays}
            aria-invalid={dueDaysError ? true : undefined}
            aria-describedby={`group-due-days-hint${dueDaysError ? ' group-due-days-error' : ''}`}
            onChange={(e) => {
              setDueDays(e.target.value)
              setDueDaysError('')
            }}
          />
          <p className="field__hint" id="group-due-days-hint">
            Dejalo vacío si las deudas nunca vencen
          </p>
          {dueDaysError && (
            <p role="alert" id="group-due-days-error" data-testid="group-due-days-error">
              {dueDaysError}
            </p>
          )}
        </div>
      )}
      {error && <p role="alert" data-testid="group-edit-error">{error}</p>}
      <div>
        <button type="submit" className="group-action" disabled={saving}>
          Guardar cambios
        </button>{' '}
        <button type="button" className="group-action" disabled={saving} onClick={onCancel}>
          Cancelar
        </button>
      </div>
    </form>
  )
}
