import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useLocation } from 'react-router'
import {
  FRIEND_REQUESTS_CHANGED_EVENT,
  listReceivedFriendshipRequests,
} from '../api/friendsClient'
import { useDismiss } from '../hooks/useDismiss'
import { BellIcon } from './NavIcons'

const DEFAULT_AVATAR = '/avatar-default.png'

export const NO_NOTIFICATIONS_MESSAGE = 'No tenés notificaciones'
export const NOTIFICATIONS_ERROR_MESSAGE = 'No se pudieron cargar tus notificaciones'

function senderName({ firstName, lastName }) {
  return lastName ? `${firstName} ${lastName}` : firstName
}

function buttonLabel(count) {
  if (count === 0) return 'Notificaciones'
  return count === 1 ? 'Notificaciones, 1 sin leer' : `Notificaciones, ${count} sin leer`
}

// Campanita (specs/015-main-screen, FR-004). Por ahora las notificaciones son
// las solicitudes de amistad pendientes; se recargan al navegar y al responder
// una solicitud desde cualquier pantalla.
export default function NotificationsMenu() {
  const { pathname } = useLocation()
  const [requests, setRequests] = useState([])
  const [failed, setFailed] = useState(false)
  const [isOpen, setIsOpen] = useState(false)
  const [reloadToken, setReloadToken] = useState(0)
  const containerRef = useRef(null)

  const close = useCallback(() => setIsOpen(false), [])
  useDismiss(containerRef, isOpen, close)

  useEffect(() => {
    let cancelled = false
    listReceivedFriendshipRequests().then((result) => {
      if (cancelled) return
      setFailed(!result.ok)
      setRequests(result.ok ? result.requests : [])
    })
    return () => {
      cancelled = true
    }
  }, [pathname, reloadToken])

  useEffect(() => {
    const reload = () => setReloadToken((token) => token + 1)
    window.addEventListener(FRIEND_REQUESTS_CHANGED_EVENT, reload)
    return () => window.removeEventListener(FRIEND_REQUESTS_CHANGED_EVENT, reload)
  }, [])

  const handleToggle = () => {
    if (!isOpen) {
      setReloadToken((token) => token + 1)
    }
    setIsOpen(!isOpen)
  }

  return (
    <div className="app-menu" ref={containerRef}>
      <button
        type="button"
        className="app-header__action"
        aria-label={buttonLabel(requests.length)}
        aria-expanded={isOpen}
        title="Notificaciones"
        onClick={handleToggle}
      >
        <BellIcon />
        {requests.length > 0 && (
          <span className="app-header__badge" aria-hidden="true">
            {requests.length > 9 ? '9+' : requests.length}
          </span>
        )}
      </button>

      {isOpen && (
        <div className="app-menu__panel app-menu__panel--end" aria-label="Notificaciones" role="region">
          <h2>Notificaciones</h2>
          {failed && <p role="alert">{NOTIFICATIONS_ERROR_MESSAGE}</p>}
          {!failed && requests.length === 0 && <p>{NO_NOTIFICATIONS_MESSAGE}</p>}
          {requests.length > 0 && (
            <ul>
              {requests.map((request) => (
                <li key={request.id} className="app-notification">
                  <img
                    src={request.sender.avatarUrl || DEFAULT_AVATAR}
                    alt=""
                    width={36}
                    height={36}
                  />
                  <span>
                    <strong>{senderName(request.sender)}</strong> te envió una solicitud de
                    amistad
                  </span>
                </li>
              ))}
            </ul>
          )}
          <Link to="/friends" onClick={close}>
            Ver solicitudes
          </Link>
        </div>
      )}
    </div>
  )
}
