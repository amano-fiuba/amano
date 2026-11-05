import { useCallback, useRef, useState } from 'react'
import { useDismiss } from '../../hooks/useDismiss'

/**
 * Acciones de administración de un miembro detrás de un botón "⋯". Es un
 * disclosure, no un role="menu": sus opciones son botones comunes que se
 * recorren con Tab, y uno de ellos abre su confirmación dentro del panel.
 * `children` recibe `close` para que cada opción decida si cierra el panel.
 */
export default function MemberActionsMenu({ memberName, testId, children }) {
  const [isOpen, setIsOpen] = useState(false)
  const containerRef = useRef(null)
  const triggerRef = useRef(null)

  const close = useCallback(() => setIsOpen(false), [])
  useDismiss(containerRef, isOpen, close)

  function handleKeyDown(event) {
    if (event.key === 'Escape') {
      triggerRef.current?.focus()
    }
  }

  return (
    <div className="member-menu" ref={containerRef} onKeyDown={handleKeyDown}>
      <button
        type="button"
        ref={triggerRef}
        className="member-menu__trigger"
        aria-label={`Acciones de ${memberName}`}
        aria-expanded={isOpen}
        data-testid={testId}
        onClick={() => setIsOpen(!isOpen)}
      >
        <span aria-hidden="true">⋯</span>
      </button>
      {isOpen && <div className="member-menu__panel">{children(close)}</div>}
    </div>
  )
}
