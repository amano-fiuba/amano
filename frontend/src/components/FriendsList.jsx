import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import {
  DELETE_FRIEND_NETWORK_ERROR_MESSAGE,
  FRIENDS_NETWORK_ERROR_MESSAGE,
  checkFriendDeletion,
  deleteFriend,
  listFriends,
} from '../api/friendsClient'
import { useAuth } from '../auth/AuthContext'
import DeleteFriendDialog from './DeleteFriendDialog'

// Exact messages of the spec (FR-007/FR-008 of 013, FR-026..FR-028 of 016),
// exported for the tests.
export const NO_FRIENDS_MESSAGE = 'Todavía no tenés amigos'
export const DELETE_SUCCESS_MESSAGE = 'Amigo eliminado correctamente'
export const NOT_FRIEND_MESSAGE = 'No sos amigo de ese usuario'

const DEFAULT_AVATAR = '/avatar-default.png'

// "Amigos desde" in DD/MM/AAAA, fixed to Argentina so the date does not depend
// on the browser's timezone (research.md D6 of 013).
const FRIENDS_SINCE_FORMAT = new Intl.DateTimeFormat('es-AR', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  timeZone: 'America/Argentina/Buenos_Aires',
})

function fullName(friend) {
  return friend.lastName ? `${friend.firstName} ${friend.lastName}` : friend.firstName
}

function friendsSince(value) {
  if (!value) return null
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? null : FRIENDS_SINCE_FORMAT.format(date)
}

// "Mis amigos" section of the "Amigos" screen (feature 013) with the
// "Eliminar amigo" action of feature 016. Owns its own loading/list/empty/error
// state, independent from "Solicitudes" (FR-010 of 013). Changing `reloadKey`
// queries the list again; a stale response never overwrites a newer one.
export default function FriendsList({ reloadKey = 0 }) {
  const { setUser } = useAuth()
  const [friends, setFriends] = useState([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState(null)
  const [retryToken, setRetryToken] = useState(0)
  const [notice, setNotice] = useState(null)
  const [checkingId, setCheckingId] = useState(null)
  const [deleteCandidate, setDeleteCandidate] = useState(null)
  const [isDeleting, setIsDeleting] = useState(false)
  const [deleteError, setDeleteError] = useState(null)

  useEffect(() => {
    let cancelled = false

    listFriends()
      .then((result) => {
        if (cancelled) return
        setIsLoading(false)
        if (result.ok) {
          setError(null)
          setFriends(result.friends)
          return
        }
        if (result.status === 401) {
          setUser(null)
          return
        }
        setFriends([])
        setError(FRIENDS_NETWORK_ERROR_MESSAGE)
      })
      .catch(() => {
        if (cancelled) return
        setIsLoading(false)
        setFriends([])
        setError(FRIENDS_NETWORK_ERROR_MESSAGE)
      })

    return () => {
      cancelled = true
    }
  }, [reloadKey, retryToken, setUser])

  async function handleDeleteRequest(friend) {
    if (checkingId !== null || isDeleting) return
    setNotice(null)
    setDeleteError(null)
    setCheckingId(friend.id)

    const result = await checkFriendDeletion(friend.id)
    setCheckingId(null)

    if (result.status === 401) {
      setUser(null)
      return
    }
    if (result.network) {
      setNotice({ kind: 'error', message: DELETE_FRIEND_NETWORK_ERROR_MESSAGE })
      return
    }
    if (result.ok) {
      if (!result.canDelete) {
        setNotice({ kind: 'error', message: result.reason })
        return
      }
      setDeleteCandidate(friend)
      return
    }
    if (result.status === 404) {
      removeFriend(friend.id)
      setNotice({ kind: 'error', message: result.detail || NOT_FRIEND_MESSAGE })
      return
    }
    setNotice({ kind: 'error', message: result.detail || DELETE_FRIEND_NETWORK_ERROR_MESSAGE })
  }

  async function handleConfirmDelete() {
    if (!deleteCandidate || isDeleting) return
    setIsDeleting(true)
    setDeleteError(null)

    const result = await deleteFriend(deleteCandidate.id)

    if (result.status === 401) {
      setUser(null)
      return
    }

    setIsDeleting(false)

    if (result.ok) {
      removeFriend(deleteCandidate.id)
      setDeleteCandidate(null)
      setNotice({ kind: 'success', message: DELETE_SUCCESS_MESSAGE })
      return
    }
    if (result.network) {
      setDeleteError(DELETE_FRIEND_NETWORK_ERROR_MESSAGE)
      return
    }
    if (result.status === 404) {
      removeFriend(deleteCandidate.id)
      setDeleteCandidate(null)
      setNotice({ kind: 'error', message: result.detail || NOT_FRIEND_MESSAGE })
      return
    }
    if (result.status === 403) {
      setDeleteCandidate(null)
      setNotice({ kind: 'error', message: result.detail || DELETE_FRIEND_NETWORK_ERROR_MESSAGE })
      return
    }
    setDeleteError(result.detail || DELETE_FRIEND_NETWORK_ERROR_MESSAGE)
  }

  function removeFriend(friendId) {
    setFriends((previous) => previous.filter((friend) => friend.id !== friendId))
  }

  function handleCancelDelete() {
    if (isDeleting) return
    setDeleteCandidate(null)
    setDeleteError(null)
  }

  const showList = !isLoading && !error

  return (
    <section className="friends-list" aria-labelledby="friends-list-title">
      <div className="friends-requests__header">
        <h2 id="friends-list-title">Mis amigos</h2>
        {showList && friends.length > 0 && (
          <span className="friends-requests__count" aria-live="polite">
            {friends.length === 1 ? '1 amigo' : `${friends.length} amigos`}
          </span>
        )}
      </div>

      {showList && notice && (
        <p
          className={`friends-requests__notice friends-requests__notice--${notice.kind}`}
          role="status"
        >
          {notice.message}
        </p>
      )}

      {isLoading && (
        <div className="friends-requests__loading" role="status">
          <span className="button-spinner" aria-hidden="true" />
          <p>Cargando amigos…</p>
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
              setRetryToken((token) => token + 1)
            }}
          >
            Reintentar
          </button>
        </div>
      )}

      {showList && friends.length === 0 && (
        <div className="friends-requests__empty friends-list__empty">
          <p>{NO_FRIENDS_MESSAGE}</p>
          <Link to="/users/search" className="friends-list__search-link">
            Buscar usuarios
          </Link>
        </div>
      )}

      {showList && friends.length > 0 && (
        <ul className="friends-requests__list" aria-label="Lista de amigos">
          {friends.map((friend) => {
            const since = friendsSince(friend.friendsSince)
            const isChecking = checkingId === friend.id
            return (
              <li
                key={friend.id}
                className="friend-request-card friend-card"
                data-testid={`friend-${friend.id}`}
              >
                <img
                  className="friend-request-card__avatar"
                  src={DEFAULT_AVATAR}
                  alt=""
                  width={44}
                  height={44}
                />
                <div className="friend-request-card__identity">
                  <strong>{fullName(friend)}</strong>
                  <span>{friend.email}</span>
                </div>
                {since && <span className="friend-card__since">Amigos desde {since}</span>}
                <div className="friend-card__actions">
                  <button
                    type="button"
                    className="friend-card__delete"
                    disabled={isChecking || isDeleting}
                    onClick={() => handleDeleteRequest(friend)}
                  >
                    {isChecking ? 'Verificando…' : 'Eliminar amigo'}
                  </button>
                </div>
              </li>
            )
          })}
        </ul>
      )}

      {deleteCandidate && (
        <DeleteFriendDialog
          friend={deleteCandidate}
          isDeleting={isDeleting}
          error={deleteError}
          onCancel={handleCancelDelete}
          onConfirm={handleConfirmDelete}
        />
      )}
    </section>
  )
}
