import { useCallback, useRef, useState } from 'react'
import { Link } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { useDismiss } from '../hooks/useDismiss'
import { PlusIcon } from './NavIcons'

const ACTIONS = [
  { to: '/groups/new', label: 'Crear grupo' },
  { to: '/expenses/new', label: 'Registrar gasto' },
  { to: '/expenses/recurring', label: 'Gastos recurrentes' },
]

// Botón "+" fijo abajo a la izquierda (specs/015-main-screen, US4).
export default function QuickActionsButton() {
  const { user } = useAuth() ?? {}
  const [isOpen, setIsOpen] = useState(false)
  const containerRef = useRef(null)

  const close = useCallback(() => setIsOpen(false), [])
  useDismiss(containerRef, isOpen, close)

  if (!user) {
    return null
  }

  return (
    <div className="quick-actions" ref={containerRef}>
      {isOpen && (
        <ul className="quick-actions__menu" aria-label="Acciones rápidas">
          {ACTIONS.map((action) => (
            <li key={action.to}>
              <Link to={action.to} onClick={close}>
                {action.label}
              </Link>
            </li>
          ))}
        </ul>
      )}
      <button
        type="button"
        className={isOpen ? 'quick-actions__toggle is-open' : 'quick-actions__toggle'}
        aria-label="Acciones rápidas"
        aria-expanded={isOpen}
        onClick={() => setIsOpen(!isOpen)}
      >
        <PlusIcon />
      </button>
    </div>
  )
}
