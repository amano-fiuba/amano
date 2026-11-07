import { useState } from 'react'
import './groups.scss'

// 022 T003: entry point for the group debt screen. The data panels arrive with
// the user stories; the tab structure and the admin-only validation tab are the
// shared skeleton built in the foundational phase.
const TABS = [
  { id: 'balances', label: 'Saldos' },
  { id: 'debts', label: 'Mis deudas' },
  { id: 'settlement', label: 'Liquidación' },
]

export default function GroupDebtsView({ isAdmin = false }) {
  const [active, setActive] = useState('balances')
  const tabs = isAdmin
    ? [...TABS, { id: 'validation', label: 'Pagos por validar' }]
    : TABS

  return (
    <section className="group-debts">
      <nav className="group-debts__tabs" aria-label="Secciones de deudas">
        {tabs.map((tab) => (
          <button
            key={tab.id}
            type="button"
            aria-pressed={active === tab.id}
            onClick={() => setActive(tab.id)}
          >
            {tab.label}
          </button>
        ))}
      </nav>
      <p className="group-debts__placeholder" data-testid="group-debts-panel">
        {tabs.find((tab) => tab.id === active)?.label}
      </p>
    </section>
  )
}
