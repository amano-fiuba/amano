import { useRef, useState } from 'react'
import { uploadGroupPhoto, GROUPS_NETWORK_ERROR_MESSAGE } from '../../api/groupsClient'

const DEFAULT_AVATAR = '/avatar-default.png'
const FORMAT_MESSAGE = 'El archivo debe ser una imagen JPG o PNG válida'

/**
 * Group photo (US8, T086): pick and upload/replace the photo (ADMIN-only;
 * the server rules). The client mirrors the existing validator's format
 * message for immediate feedback and keeps the current photo rendered on any
 * failure (FR-021); success swaps the URL for the freshly returned one.
 */
export default function GroupPhotoPanel({ groupId, currentPhoto = null, isAdmin = false }) {
  const [photo, setPhoto] = useState(currentPhoto)
  const [status, setStatus] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const inputRef = useRef(null)

  function pickClientSideError(file) {
    if (!file) {
      return 'Debés seleccionar un archivo de imagen'
    }
    if (file.type !== 'image/jpeg' && file.type !== 'image/png') {
      return FORMAT_MESSAGE
    }
    return null
  }

  async function handleChange(event) {
    const file = event.target.files?.[0]
    setBusy(true)
    setError('')
    setStatus('')
    const clientError = pickClientSideError(file)
    if (clientError) {
      setError(clientError)
      setBusy(false)
      return
    }
    const result = await uploadGroupPhoto(groupId, file)
    if (!result || result.network) {
      setError(GROUPS_NETWORK_ERROR_MESSAGE)
    } else if (result.ok) {
      setPhoto(result.photo) // replaces the previous photo only on success
      setStatus('La foto del grupo se actualizó')
    } else {
      setError(result.detail || 'No se pudo actualizar la foto del grupo')
    }
    setBusy(false)
  }

  return (
    <section data-testid="group-photo" aria-label="Foto del grupo">
      <img
        src={photo || DEFAULT_AVATAR}
        alt=""
        width={96}
        height={96}
        data-testid="group-photo-image"
      />
      {status && <p role="status">{status}</p>}
      {error && (
        <p role="alert" data-testid="group-photo-error">
          {error}
        </p>
      )}
      {!isAdmin && (
        <p role="alert" data-testid="group-photo-admin-hint">
          Solo un administrador puede cambiar la foto del grupo
        </p>
      )}
      <label htmlFor="group-photo-input">Cambiar foto del grupo</label>
      <input
        id="group-photo-input"
        ref={inputRef}
        type="file"
        accept="image/jpeg,image/png"
        disabled={busy || !isAdmin}
        onChange={handleChange}
        data-testid="group-photo-input"
      />
      <button type="button" disabled={!isAdmin} onClick={() => inputRef.current?.click()}>
        Cambiar foto
      </button>
    </section>
  )
}
