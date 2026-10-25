// Edición del perfil (specs/005-edit-user-profile): formulario precargado con
// los datos frescos del servidor (me(), FR-002), edición de nombre/apellido/
// CBU/CVU y monedas con las reglas de 001 reutilizadas (FR-003..FR-006), correo
// SIEMPRE deshabilitado (FR-007: identidad invariante de la cuenta), avatar
// reemplazable/eliminable (US3) y cancelación sin guardar (FR-008). Guardar
// confirma el éxito y muestra los datos frescos (FR-009/FR-012); fallos de
// red/servidor conservan los datos (FR-019). La actualización vive en el
// contexto de sesión (setUser) para que el resto de la app vea los cambios.
import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate } from 'react-router'
import {
  UPDATE_NETWORK_ERROR_MESSAGE,
  deleteAvatar,
  me,
  updateAvatar,
  updateProfile,
} from '../api/authClient'
import {
  FORMAT_MESSAGE,
  realContentType,
} from '../validation/avatarValidation'
import TextField from '../components/TextField'
import { useAuth } from '../auth/AuthContext'
import {
  AVATAR_MAX_BYTES,
  normalizeCbu,
  trim,
  validateCbu,
  validateFirstName,
  validateLastName,
} from '../validation/registerValidation'

// Mensajes de avatar compartidos con el registro (mismo backend).
const AVATAR_SIZE_MESSAGE = 'El archivo es demasiado grande (máximo 2 MB)'

const MONEDAS = [
  { code: 'ARS', label: 'Peso argentino (ARS)' },
  { code: 'USD', label: 'Dólar estadounidense (USD)' },
  { code: 'EUR', label: 'Euro (EUR)' },
]

export default function ProfileEditPage() {
  const { user, setUser } = useAuth()
  const navigate = useNavigate()
  const {
    handleSubmit,
    register: bindField,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm({ mode: 'onBlur' })

  const [loadError, setLoadError] = useState(false)
  const [formError, setFormError] = useState('')
  const [success, setSuccess] = useState('')
  const [currencies, setCurrencies] = useState([])
  const [avatarUrl, setAvatarUrl] = useState('')
  const [hasCustomAvatar, setHasCustomAvatar] = useState(false)
  const [avatarError, setAvatarError] = useState('')
  const [avatarBusy, setAvatarBusy] = useState(false)

  const aplicarPerfil = (profile) => {
    reset({
      firstName: profile.firstName ?? '',
      lastName: profile.lastName ?? '',
      cbuCvu: profile.cbuCvu ?? '',
    })
    setCurrencies(Array.isArray(profile.currencies) ? profile.currencies : [])
    setHasCustomAvatar(Boolean(profile.hasCustomAvatar))
    setAvatarUrl(profile.hasCustomAvatar && profile.avatarUrl ? profile.avatarUrl : '')
  }

  // FR-002: precarga fresca del servidor (sin caché), con estados loading/
  // datos/error como en 002. El correo precargado es el del servidor y queda
  // deshabilitado. La estructura (.then/.catch con todos los setState dentro
  // de continuaciones asíncronas) sigue el patrón de ProfilePage (002).
  useEffect(() => {
    let cancelled = false
    me().then((result) => {
      if (cancelled) {
        return
      }
      if (result.ok) {
        const profile = result.profile
        setUser(profile)
        aplicarPerfil(profile)
        setAvatarError('')
      } else if (result.network) {
        // FR-019: mensaje, sin datos incompletos ni ficticios.
        setLoadError(true)
      }
      // 401 (sin sesión): el gate de la ruta redirige.
    })
      .catch(() => {
        if (!cancelled) setLoadError(true)
      })
    return () => {
      cancelled = true
    }
  }, [setUser]) // eslint-disable-line react-hooks/exhaustive-deps

  const toggleMoneda = (code) => {
    setCurrencies((prev) =>
      prev.includes(code) ? prev.filter((item) => item !== code) : [...prev, code],
    )
  }

  const onSubmit = async (values) => {
    setFormError('')
    setSuccess('')
    const result = await updateProfile({
      firstName: trim(values.firstName),
      lastName: trim(values.lastName ?? ''),
      cbuCvu: values.cbuCvu ? normalizeCbu(values.cbuCvu) : '',
      currencies,
    })

    if (result.ok) {
      // FR-009/FR-012: confirmación + datos frescos del servidor en pantalla
      // y en el contexto de sesión (la visualización de 002 los refleja).
      setUser(result.profile)
      aplicarPerfil(result.profile)
      setSuccess('Los cambios se guardaron correctamente')
      return
    }
    if (result.network || result.status >= 500) {
      setFormError(UPDATE_NETWORK_ERROR_MESSAGE)
      return
    }
    const { fields, detail } = result
    const fieldNames = ['firstName', 'lastName', 'cbuCvu']
    if (fields) {
      for (const [field, message] of Object.entries(fields)) {
        if (fieldNames.includes(field)) {
          setError(field, { type: 'server', message })
          continue
        }
        if (field === 'currencies') {
          setFormError(message)
          return
        }
      }
    }
    if (detail) {
      setFormError(detail)
    } else {
      setFormError(UPDATE_NETWORK_ERROR_MESSAGE)
    }
  }

  const subirAvatar = async (event) => {
    const file = event.target.files?.[0] ?? null
    event.target.value = '' // permitir reintentar el mismo archivo
    if (!file) return
    setAvatarError('')
    setSuccess('')
    // Validación en vivo igual que en el registro (FR-010/FR-013 de 001):
    // tamaño primero, luego magic bytes reales.
    if (file.size > AVATAR_MAX_BYTES) {
      setAvatarError(AVATAR_SIZE_MESSAGE)
      return
    }
    const kind = await realContentType(file)
    if (kind === null) {
      setAvatarError(FORMAT_MESSAGE)
      return
    }
    setAvatarBusy(true)
    const result = await updateAvatar(file)
    setAvatarBusy(false)
    if (result.ok) {
      setUser(result.profile)
      setHasCustomAvatar(Boolean(result.profile?.hasCustomAvatar))
      setAvatarUrl(result.profile?.avatarUrl ?? '')
      setSuccess('Los cambios se guardaron correctamente')
      return
    }
    if (result.network) {
      setFormError(UPDATE_NETWORK_ERROR_MESSAGE)
      return
    }
    setAvatarError(result.fields?.avatar ?? result.detail ?? UPDATE_NETWORK_ERROR_MESSAGE)
  }

  const eliminarAvatar = async () => {
    setAvatarError('')
    setSuccess('')
    setAvatarBusy(true)
    const result = await deleteAvatar()
    setAvatarBusy(false)
    if (result.ok) {
      setHasCustomAvatar(false)
      setAvatarUrl('')
      setSuccess('Los cambios se guardaron correctamente')
      return
    }
    if (result.network) {
      setFormError(UPDATE_NETWORK_ERROR_MESSAGE)
      return
    }
    setAvatarError(result.fields?.avatar ?? result.detail ?? UPDATE_NETWORK_ERROR_MESSAGE)
  }

  if (loadError) {
    return (
      <main className="page">
        <h1>Modificar perfil</h1>
        <p role="alert">No se pudo cargar el perfil. Intenta nuevamente</p>
      </main>
    )
  }

  const previewUrl = avatarUrl || '/avatar-default.png'
  const rules = (validate) => ({
    validate: (value) => {
      const message = validate(value)
      return message === '' || message
    },
  })

  return (
    <main className="page">
      <h1>Modificar perfil</h1>
      {success && (
        <p role="status" data-testid="form-success">
          {success}
        </p>
      )}
      {formError && (
        <p role="alert" data-testid="form-error">
          {formError}
        </p>
      )}

      {/* US3: avatar procesado independiente de los datos del formulario. */}
      <img
        src={previewUrl}
        alt={`Avatar de ${user?.firstName ?? 'usuario'}`}
        className="app-avatar"
        width={96}
        height={96}
      />
      <div className="avatar-actions">
        <label htmlFor="avatar-edit">
          Avatar: <input
            id="avatar-edit"
            type="file"
            accept="image/jpeg,image/png"
            onChange={subirAvatar}
            disabled={avatarBusy}
          />
        </label>
        {hasCustomAvatar && (
          <button type="button" onClick={eliminarAvatar} disabled={avatarBusy}>
            Eliminar avatar
          </button>
        )}
      </div>
      {avatarError && (
        <p role="alert" data-testid="avatar-error">
          {avatarError}
        </p>
      )}

      <form onSubmit={handleSubmit(onSubmit)} noValidate>
        {/* FR-007: el correo es la identidad de la cuenta; solo lectura. */}
        <div className="field">
          <label htmlFor="email-readonly">Correo electrónico</label>
          <input
            id="email-readonly"
            value={user?.email ?? ''}
            readOnly
            disabled
            aria-describedby="email-readonly-note"
          />
          <p id="email-readonly-note">El correo electrónico no puede modificarse</p>
        </div>

        <TextField
          label="Nombre"
          error={errors.firstName?.message}
          {...bindField('firstName', {
            required: 'El nombre es obligatorio',
            ...rules(validateFirstName),
          })}
        />

        <TextField
          label="Apellido"
          error={errors.lastName?.message}
          {...bindField('lastName', rules(validateLastName))}
        />

        <TextField
          label="CBU/CVU"
          error={errors.cbuCvu?.message}
          {...bindField('cbuCvu', rules(validateCbu))}
        />

        <fieldset>
          <legend>Monedas preferidas (opcional; vacío = ARS por defecto)</legend>
          {MONEDAS.map(({ code, label }) => (
            <label key={code}>
              <input
                type="checkbox"
                checked={currencies.includes(code)}
                onChange={() => toggleMoneda(code)}
              />
              {label}
            </label>
          ))}
        </fieldset>

        {/* FR-011: deshabilitado durante el envío; rehabilitado ante cualquier
            error por react-hook-form (isSubmitting). */}
        <div className="form-actions">
          <button type="submit" disabled={isSubmitting}>
            Guardar cambios
          </button>
          {/* Vuelve a la visualización de 002 (los cambios exitosos ya quedaron guardados). */}
          <button type="button" onClick={() => navigate('/profile')}>
            Volver atrás
          </button>
        </div>
      </form>

      <p>
        <Link to="/profile/password">Cambiar contraseña</Link>
      </p>
    </main>
  )
}
