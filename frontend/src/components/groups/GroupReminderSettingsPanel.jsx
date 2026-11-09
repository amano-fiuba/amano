import { useEffect, useState } from 'react'
import {
  getReminderSettings,
  updateReminderSettings,
  GROUPS_NETWORK_ERROR_MESSAGE,
} from '../../api/groupsClient'
import { useSnackbar } from '../Snackbar'

const ALLOWED_SEND_DAYS = [7, 5, 3, 2, 1]
const DEFAULT_SEND_DAYS = 5
const MAX_MESSAGE_LENGTH = 500

function sendDaysLabel(days) {
  return days === 1 ? '1 día antes' : `${days} días antes`
}

/**
 * Admin-only reminder settings (025 US2/US4): when to send the reminder and the
 * optional custom message. Only rendered for admins; the backend still enforces
 * the role.
 */
export default function GroupReminderSettingsPanel({ groupId, isAdmin }) {
  const showSnackbar = useSnackbar()
  const [sendDays, setSendDays] = useState(DEFAULT_SEND_DAYS)
  const [message, setMessage] = useState('')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState({})

  useEffect(() => {
    if (!isAdmin) {
      return undefined
    }
    let cancelled = false
    getReminderSettings(groupId).then((result) => {
      if (cancelled) return
      if (result.ok) {
        setSendDays(result.settings.sendDays)
        setMessage(result.settings.message ?? '')
      } else if (result.network) {
        setError(GROUPS_NETWORK_ERROR_MESSAGE)
      } else {
        setError(result.detail || 'No se pudo cargar la configuración de recordatorios')
      }
      setLoading(false)
    })
    return () => {
      cancelled = true
    }
  }, [groupId, isAdmin])

  if (!isAdmin) {
    return null
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setSaving(true)
    setError('')
    setFieldErrors({})
    const result = await updateReminderSettings(groupId, { sendDays, message })
    if (result.ok) {
      setSendDays(result.settings.sendDays)
      setMessage(result.settings.message ?? '')
      showSnackbar('Recordatorios actualizados')
    } else if (result.network) {
      setError(GROUPS_NETWORK_ERROR_MESSAGE)
    } else {
      setFieldErrors(result.fields ?? {})
      setError(result.detail || 'No se pudieron guardar los recordatorios')
    }
    setSaving(false)
  }

  return (
    <section className="group-reminder-settings" data-testid="reminder-settings-panel">
      <h3>Recordatorios de deudas</h3>
      {loading ? (
        <p>Cargando…</p>
      ) : (
        <form onSubmit={handleSubmit}>
          <label htmlFor="reminder-send-days">Cuándo enviar el recordatorio</label>
          <select
            id="reminder-send-days"
            value={sendDays}
            onChange={(event) => setSendDays(Number(event.target.value))}
          >
            {ALLOWED_SEND_DAYS.map((days) => (
              <option key={days} value={days}>
                {sendDaysLabel(days)}
              </option>
            ))}
          </select>
          {fieldErrors.sendDays && <p role="alert">{fieldErrors.sendDays}</p>}

          <label htmlFor="reminder-message">Mensaje personalizado (opcional)</label>
          <textarea
            id="reminder-message"
            maxLength={MAX_MESSAGE_LENGTH}
            rows={3}
            value={message}
            aria-describedby="reminder-message-help reminder-message-counter"
            onChange={(event) => setMessage(event.target.value)}
          />
          <p id="reminder-message-help" className="field-help">
            Se incluye en el correo del recordatorio. Si lo dejás vacío se usa el mensaje
            predeterminado.
          </p>
          <p id="reminder-message-counter" data-testid="reminder-message-counter">
            {message.length}/{MAX_MESSAGE_LENGTH}
          </p>
          {fieldErrors.message && <p role="alert">{fieldErrors.message}</p>}

          <button type="submit" disabled={saving}>
            {saving ? 'Guardando…' : 'Guardar recordatorios'}
          </button>
        </form>
      )}
      {error && (
        <p role="alert" data-testid="reminder-settings-error">
          {error}
        </p>
      )}
    </section>
  )
}
