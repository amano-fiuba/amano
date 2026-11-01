import { useEffect, useState } from 'react'
import {
  RECEIVED_NETWORK_ERROR_MESSAGE,
  RESPOND_NETWORK_ERROR_MESSAGE,
  listReceivedFriendshipRequests,
  respondFriendshipRequest,
} from '../api/friendsClient'
import { useAuth } from '../auth/AuthContext'

// Exact messages of the spec (FR-013), exported for the tests.
export const NO_PENDING_REQUESTS_MESSAGE = 'No tenés solicitudes de amistad pendientes'
export const ACCEPTED_MESSAGE = 'Solicitud de amistad aceptada'
export const REJECTED_MESSAGE = 'Solicitud de amistad rechazada'

const DEFAULT_AVATAR = '/avatar-default.png'
const ACCEPT_LABEL = 'Aceptar'
const REJECT_LABEL = 'Rechazar'

function fullName(sender) {
  return sender.lastName ? `${sender.firstName} ${sender.lastName}` : sender.firstName
}

// "Amigos" screen (features 011 US3 and 012 US1/US2/US3). The "Solicitudes"
// section lists the pending requests received by the session user and lets the
// recipient accept ("ACCEPT") or reject ("REJECT") each one. The row leaves the
// list on 200/409/404 (it is no longer pending), the exact confirmation stays
// visible until another action or navigation (research.md D8), network failures
// keep the row reactive and 401 closes the session. The friends list itself
// belongs to a later story.
export default function FriendsPage() {
  const { setUser } = useAuth()
  const [requests, setRequests] = useState([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState(null)
  const [reloadToken, setReloadToken] = useState(0)
  const [responding, setResponding] = useState(null)
  const [notice, setNotice] = useState(null)

  useEffect(() => {
    let cancelled = false

    listReceivedFriendshipRequests()
      .then((result) => {
        if (cancelled) return
        setIsLoading(false)
        if (result.ok) {
          setRequests(result.requests)
          return
        }
        if (result.status === 401) {
          setUser(null)
          return
        }
        setRequests([])
        setError(RECEIVED_NETWORK_ERROR_MESSAGE)
      })
      .catch(() => {
        if (cancelled) return
        setIsLoading(false)
        setRequests([])
        setError(RECEIVED_NETWORK_ERROR_MESSAGE)
      })

    return () => {
      cancelled = true
    }
  }, [reloadToken, setUser])

  async function handleRespond(request, action) {
    if (responding) return
    setResponding({ requestId: request.id, action })
    setNotice(null)
    const result = await respondFriendshipRequest(request.id, action)
    if (result.status === 401) {
      setUser(null)
      return
    }
    const removed = request.id
    setResponding(null)
    if (result.ok) {
      setRequests((previous) => previous.filter((item) => item.id !== removed))
      setNotice({
        kind: 'success',
        message:
          action === 'ACCEPT' ? ACCEPTED_MESSAGE : REJECTED_MESSAGE,
      })
      return
    }
    if (result.network) {
      // The row stays in the list (research.md D8): it is still pending as far
      // as we know, and its actions go back to being available.
      setNotice({ kind: 'error', message: RESPOND_NETWORK_ERROR_MESSAGE })
      return
    }
    if (result.status === 409 || result.status === 404) {
      setRequests((previous) => previous.filter((item) => item.id !== removed))
      setNotice({ kind: 'conflict', message: result.detail })
      return
    }
    const fieldMessage = result.fields?.action || result.fields?.id
    setNotice({ kind: 'conflict', message: fieldMessage || result.detail })
  }

  const rowActionState = (request) => {
    if (!responding) return 'idle'
    if (responding.requestId !== request.id) return 'idle'
    return responding.action === 'ACCEPT' ? 'accepting' : 'rejecting'
  }

  return (
    <main className="page friends-page">
      <header className="friends-page__header">
        <h1>Amigos</h1>
        <p className="friends-page__lead">
          Revisá las solicitudes de amistad que recibiste.
        </p>
      </header>

      <section className="friends-requests" aria-labelledby="friends-requests-title">
        <div className="friends-requests__header">
          <h2 id="friends-requests-title">Solicitudes</h2>
          {!isLoading && !error && requests.length > 0 && (
            <span className="friends-requests__count" aria-live="polite">
              {requests.length === 1 ? '1 pendiente' : `${requests.length} pendientes`}
            </span>
          )}
        </div>

        {isLoading && (
          <div className="friends-requests__loading" role="status">
            <span className="button-spinner" aria-hidden="true" />
            <p>Cargando solicitudes…</p>
          </div>
        )}

        {!isLoading && error && (
          <div className="friends-requests__error" role="alert">
            <p>{error}</p>
            <button
              type="button"
              className="friends-requests__retry"
              onClick={() => {
                setIsLoading(true)
                setError(null)
                setReloadToken((token) => token + 1)
              }}
            >
              Reintentar
            </button>
          </div>
        )}

        {!isLoading && !error && notice && (
          <p
            className={`friends-requests__notice friends-requests__notice--${notice.kind}`}
            role="status"
          >
            {notice.message}
          </p>
        )}

        {!isLoading && !error && !notice && requests.length === 0 && (
          <p className="friends-requests__empty" role="status">
            {NO_PENDING_REQUESTS_MESSAGE}
          </p>
        )}

        {!isLoading && !error && requests.length > 0 && (
          <ul className="friends-requests__list" aria-label="Solicitudes pendientes">
            {requests.map((request) => {
              const actionState = rowActionState(request)
              return (
                <li
                  key={request.id}
                  className="friend-request-card"
                  data-testid={`friend-request-${request.id}`}
                >
                  <img
                    className="friend-request-card__avatar"
                    src={DEFAULT_AVATAR}
                    alt=""
                    width={44}
                    height={44}
                  />
                  <div className="friend-request-card__identity">
                    <strong>{fullName(request.sender)}</strong>
                    <span>{request.sender.email}</span>
                  </div>
                  <span className="friend-request-card__status">Pendiente</span>
                  <div className="friend-request-card__actions">
                    <button
                      type="button"
                      className="friend-request-card__accept"
                      disabled={actionState !== 'idle'}
                      onClick={() => handleRespond(request, 'ACCEPT')}
                    >
                      {actionState === 'accepting' ? 'Aceptando…' : ACCEPT_LABEL}
                    </button>
                    <button
                      type="button"
                      className="friend-request-card__reject"
                      disabled={actionState !== 'idle'}
                      onClick={() => handleRespond(request, 'REJECT')}
                    >
                      {actionState === 'rejecting' ? 'Rechazando…' : REJECT_LABEL}
                    </button>
                  </div>
                </li>
              )
            })}
          </ul>
        )}
      </section>
    </main>
  )
}
