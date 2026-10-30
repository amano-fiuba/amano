import { useEffect, useState } from 'react'
import {
  RECEIVED_NETWORK_ERROR_MESSAGE,
  listReceivedFriendshipRequests,
} from '../api/friendsClient'
import { useAuth } from '../auth/AuthContext'

// Exact messages of the spec (FR-013, FR-019), exported for the tests.
export const NO_PENDING_REQUESTS_MESSAGE = 'No tenés solicitudes de amistad pendientes'

const DEFAULT_AVATAR = '/avatar-default.png'

function fullName(sender) {
  return sender.lastName ? `${sender.firstName} ${sender.lastName}` : sender.firstName
}

// "Amigos" screen (feature 011, US3). In 011 it only contains the "Solicitudes"
// section with the pending requests received by the session user; the friends
// list and accepting/rejecting belong to a later story, so no actions are shown.
export default function FriendsPage() {
  const { setUser } = useAuth()
  const [requests, setRequests] = useState([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState(null)
  const [reloadToken, setReloadToken] = useState(0)

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

        {!isLoading && !error && requests.length === 0 && (
          <p className="friends-requests__empty" role="status">
            {NO_PENDING_REQUESTS_MESSAGE}
          </p>
        )}

        {!isLoading && !error && requests.length > 0 && (
          <ul className="friends-requests__list" aria-label="Solicitudes pendientes">
            {requests.map((request) => {
              const name = fullName(request.sender)
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
                    <strong>{name}</strong>
                    <span>{request.sender.email}</span>
                  </div>
                  <span className="friend-request-card__status">Pendiente</span>
                </li>
              )
            })}
          </ul>
        )}
      </section>
    </main>
  )
}
