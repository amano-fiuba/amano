import { useEffect, useState } from 'react'
import { getActivity, GROUPS_NETWORK_ERROR_MESSAGE } from '../../api/groupsClient'

const EVENT_LABELS = {
  MEMBER_ADDED: 'Agregó un miembro',
  MEMBER_REMOVED: 'Quitó un miembro',
  MEMBER_LEFT: 'Abandonó el grupo',
  PERMISSION_CHANGED: 'Cambió los permisos del grupo',
  PHOTO_CHANGED: 'Cambió la foto del grupo',
  EXPENSE_CREATED: 'Registró un gasto',
  PAYMENT_MADE: 'Registró un pago',
}

function formatDate(value) {
  try {
    return new Intl.DateTimeFormat('es-AR', {
      dateStyle: 'medium',
      timeStyle: 'short',
    }).format(new Date(value))
  } catch {
    return value
  }
}

export default function GroupHistoryView({ groupId }) {
  const [entries, setEntries] = useState(null)
  const [warning, setWarning] = useState('')
  const [error, setError] = useState('')
  const [page, setPage] = useState(1)
  const [totalPages, setTotalPages] = useState(1)

  useEffect(() => {
    let cancelled = false
    getActivity(groupId, { page, size: 20 }).then((result) => {
      if (cancelled) return
      if (result.ok) {
        setEntries(result.content)
        setTotalPages(Math.max(result.totalPages, 1))
        if (!result.content?.length) {
          setWarning('Todavía no hay actividad en este grupo')
        } else {
          setWarning('')
        }
      } else if (result.network) {
        setError(GROUPS_NETWORK_ERROR_MESSAGE)
      } else {
        setError(result.detail || 'No existe el grupo solicitado o no sos miembro')
      }
    })
    return () => {
      cancelled = true
    }
  }, [groupId, page])

  function eventLabel(entry) {
    if (EVENT_LABELS[entry.eventType]) {
      return EVENT_LABELS[entry.eventType]
    }
    return 'Evento del grupo'
  }

  return (
    <main className="page">
      <h1>Historial de actividad</h1>
      {warning && <p role="status">{warning}</p>}
      {error && (
        <p role="alert" data-testid="history-error">
          {error}
        </p>
      )}
      {entries && (
        <section data-testid="history-list">
          {entries.map((entry) => (
            <article key={entry.id} data-testid="activity-entry">
              <p>
                <strong>{entry.actor ? `${entry.actor.firstName} ${entry.actor.lastName}` : 'Sistema'}</strong>{' '}
                {eventLabel(entry)}
              </p>
              <p>
                <time dateTime={entry.createdAt}>{formatDate(entry.createdAt)}</time>
              </p>
            </article>
          ))}
        </section>
      )}
      <p data-testid="history-hook-note">
        Los gastos y pagos se registrarán cuando exista la integración de gastos por grupo.
      </p>
      {page > 1 && (
        <button type="button" onClick={() => setPage(page - 1)}>
          Anterior
        </button>
      )}
      {page < totalPages && totalPages > 1 && (
        <button type="button" onClick={() => setPage(page + 1)}>
          Siguiente
        </button>
      )}
    </main>
  )
}
