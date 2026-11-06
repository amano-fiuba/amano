import { useEffect, useRef } from 'react'

const MESSAGES = {
  confirm: '¿Seguro que querés abandonar el grupo?',
  'last-admin': 'Sos el último administrador: asigná otro administrador antes de salir',
  'last-member': 'Sos el último miembro: si confirmas, el grupo se eliminará',
}

/**
 * Leave-group modal (spec 020). It only renders one of the three modes; the
 * API call lives in LeaveGroupPanel. `last-admin` offers no way out: just the
 * close button. Same dialog pattern and styles as DeleteFriendDialog.
 */
export default function LeaveGroupModal({ mode, busy = false, error = '', onConfirm, onClose }) {
  const closeRef = useRef(null)

  useEffect(() => {
    closeRef.current?.focus()
  }, [])

  useEffect(() => {
    function handleKeyDown(event) {
      if (event.key === 'Escape' && !busy) {
        onClose()
      }
    }
    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [busy, onClose])

  return (
    <div className="delete-friend-dialog__backdrop">
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="leave-group-title"
        aria-describedby="leave-group-message"
        className="delete-friend-dialog leave-group-modal"
        data-testid="leave-group-dialog"
      >
        <button
          type="button"
          ref={closeRef}
          className="leave-group-modal__close"
          aria-label="Cerrar"
          disabled={busy}
          onClick={onClose}
        >
          <span aria-hidden="true">×</span>
        </button>
        <h3 id="leave-group-title">Abandonar el grupo</h3>
        <p id="leave-group-message" data-testid="leave-group-message">{MESSAGES[mode]}</p>
        {error && (
          <p className="delete-friend-dialog__error" role="alert" data-testid="leave-group-error">
            {error}
          </p>
        )}
        {mode !== 'last-admin' && (
          <div className="delete-friend-dialog__actions">
            <button
              type="button"
              className="delete-friend-dialog__cancel"
              disabled={busy}
              onClick={onClose}
            >
              Cancelar
            </button>
            <button
              type="button"
              className="delete-friend-dialog__confirm"
              disabled={busy}
              onClick={onConfirm}
            >
              Confirmar
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
