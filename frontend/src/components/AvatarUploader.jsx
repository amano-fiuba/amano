import { useState } from 'react'
import { AVATAR_MAX_BYTES } from '../validation/registerValidation'

// US3 optional avatar uploader (task T036; FR-003, FR-010, FR-013, FR-022, D6):
// live validation with Spanish messages — size first, then the REAL content is
// inspected via magic bytes (the declared MIME is a hint, never the truth).
// Without a file the field stays clean and never blocks the rest of the form.
const FORMAT_MESSAGE = 'El archivo debe ser una imagen JPG o PNG válida'
const SIZE_MESSAGE = 'El archivo es demasiado grande (máximo 2 MB)'

async function realContentType(file) {
  // reads only the leading bytes: JPEG FF D8 FF / PNG 89 50 4E 47 (same as backend)
  const head = new Uint8Array(await file.slice(0, 4).arrayBuffer())
  if (head[0] === 0xff && head[1] === 0xd8 && head[2] === 0xff) {
    return 'jpeg'
  }
  if (head[0] === 0x89 && head[1] === 0x50 && head[2] === 0x4e && head[3] === 0x47) {
    return 'png'
  }
  return null
}

export default function AvatarUploader({ onFileSelected }) {
  const [error, setError] = useState('')
  const [fileName, setFileName] = useState('')

  const handleChange = async (event) => {
    const file = event.target.files?.[0] ?? null
    if (!file) {
      setError('')
      setFileName('')
      onFileSelected?.(null)
      return
    }

    // 1. size (2 MB ceiling, FR-010)
    if (file.size > AVATAR_MAX_BYTES) {
      setError(SIZE_MESSAGE)
      // only resets an earlier valid selection (invalid picks deliver nothing)
      if (fileName) {
        onFileSelected?.(null)
      }
      return
    }
    // 2. real content (magic bytes, D6) — the declared MIME is not trusted
    const kind = await realContentType(file)
    if (kind === null) {
      setError(FORMAT_MESSAGE)
      if (fileName) {
        onFileSelected?.(null)
      }
      return
    }

    setError('')
    setFileName(file.name)
    onFileSelected?.(file)
  }

  const handleClear = () => {
    setError('')
    setFileName('')
    onFileSelected?.(null)
  }

  return (
    <div>
      <label>
        Avatar (opcional){' '}
        <input
          type="file"
          /* FR-010: only jpg/png are accepted by the backend either way. */
          accept="image/jpeg,image/png"
          onChange={handleChange}
        />
      </label>
      {fileName && !error && (
        <button type="button" onClick={handleClear}>
          Quitar
        </button>
      )}
      {fileName && !error && <p>Selección: {fileName}</p>}
      {error && <p role="alert">{error}</p>}
    </div>
  )
}
