import { useRef, useState } from 'react'
import { uploadGroupPhoto, GROUPS_NETWORK_ERROR_MESSAGE } from '../../api/groupsClient'
import { CameraIcon } from '../NavIcons'
import { useSnackbar } from '../Snackbar'

const DEFAULT_AVATAR = '/avatar-default.png'
const FORMAT_MESSAGE = 'El archivo debe ser una imagen JPG o PNG válida'

/**
 * Group photo (US8, T086): pick and upload/replace the photo (ADMIN-only;
 * the server rules). The client mirrors the existing validator's format
 * message for immediate feedback and keeps the current photo rendered on any
 * failure (FR-021); success swaps the URL for the freshly returned one.
 */
export default function GroupPhotoPanel({ groupId, currentPhoto = null, isAdmin = false }) {
  const showSnackbar = useSnackbar()
  const [photo, setPhoto] = useState(currentPhoto)
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
      showSnackbar('La foto del grupo se actualizó')
    } else {
      setError(result.detail || 'No se pudo actualizar la foto del grupo')
    }
    setBusy(false)
  }

  return (
    <section data-testid="group-photo" aria-label="Foto del grupo" className="group-photo">
      <div className="group-photo__frame">
        <img
          src={photo || DEFAULT_AVATAR}
          alt=""
          width={96}
          height={96}
          data-testid="group-photo-image"
        />
        {isAdmin && (
          <>
            <label className="visually-hidden" htmlFor="group-photo-input">Cambiar foto del grupo</label>
            <input
              id="group-photo-input"
              ref={inputRef}
              type="file"
              accept="image/jpeg,image/png"
              disabled={busy}
              onChange={handleChange}
              data-testid="group-photo-input"
              hidden
            />
            <button
              type="button"
              className="group-photo__change"
              aria-label="Cambiar foto"
              disabled={busy}
              onClick={() => inputRef.current?.click()}
            >
              <CameraIcon />
            </button>
          </>
        )}
      </div>
      {error && (
        <p role="alert" className="group-photo__error" data-testid="group-photo-error">
          {error}
        </p>
      )}
    </section>
  )
}
