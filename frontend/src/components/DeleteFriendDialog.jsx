import { useEffect, useRef } from 'react'

export const DELETE_DIALOG_TITLE = 'Eliminar amigo'
export const DELETE_CANCEL_LABEL = 'Cancelar'
export const DELETE_CONFIRM_LABEL = 'Eliminar'
export const DELETING_LABEL = 'Eliminando…'

function deleteDialogMessage(friend) {
  const name = friend?.lastName
    ? `${friend.firstName} ${friend.lastName}`
    : friend?.firstName ?? ''
  return `¿Querés eliminar a ${name} de tus amigos?`
}

// Accessible confirmation dialog for deleting a friend (feature 016, FR-004..
// FR-006). It only renders the confirmation; the API calls live in FriendsList.
export default function DeleteFriendDialog({
  friend,
  isDeleting = false,
  error = null,
  onCancel,
  onConfirm,
}) {
  const cancelRef = useRef(null)

  useEffect(() => {
    cancelRef.current?.focus()
  }, [])

  useEffect(() => {
    function handleKeyDown(event) {
      if (event.key === 'Escape' && !isDeleting) {
        onCancel()
      }
    }
    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [isDeleting, onCancel])

  return (
    <div className="delete-friend-dialog__backdrop">
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="delete-friend-dialog-title"
        aria-describedby="delete-friend-dialog-message"
        className="delete-friend-dialog"
        data-testid="delete-friend-dialog"
      >
        <h3 id="delete-friend-dialog-title">{DELETE_DIALOG_TITLE}</h3>
        <p id="delete-friend-dialog-message">{deleteDialogMessage(friend)}</p>
        {error && (
          <p className="delete-friend-dialog__error" role="alert">
            {error}
          </p>
        )}
        <div className="delete-friend-dialog__actions">
          <button
            type="button"
            className="delete-friend-dialog__cancel"
            ref={cancelRef}
            disabled={isDeleting}
            onClick={onCancel}
          >
            {DELETE_CANCEL_LABEL}
          </button>
          <button
            type="button"
            className="delete-friend-dialog__confirm"
            disabled={isDeleting}
            onClick={onConfirm}
          >
            {isDeleting ? DELETING_LABEL : DELETE_CONFIRM_LABEL}
          </button>
        </div>
      </div>
    </div>
  )
}
