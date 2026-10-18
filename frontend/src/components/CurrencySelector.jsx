import { useState } from 'react'

// US3 optional currency selector (task T036; FR-003, FR-011, Q1): multi-select
// of ARS/USD/EUR in click order; an empty selection persists nothing and the
// client shows the ARS default (Q1). Never blocks the rest of the form.
const CURRENCIES = [
  { code: 'ARS', label: 'Peso argentino (ARS)' },
  { code: 'USD', label: 'Dólar estadounidense (USD)' },
  { code: 'EUR', label: 'Euro (EUR)' },
]

export default function CurrencySelector({ onChange }) {
  const [selected, setSelected] = useState([])

  const toggle = (code) => {
    const next = selected.includes(code)
      ? selected.filter((item) => item !== code)
      : [...selected, code]
    setSelected(next)
    onChange?.(next)
  }

  return (
    <fieldset>
      <legend>Monedas (opcional, default ARS)</legend>
      {CURRENCIES.map(({ code, label }) => (
        <label key={code}>
          <input
            type="checkbox"
            checked={selected.includes(code)}
            onChange={() => toggle(code)}
          />
          {label}
        </label>
      ))}
    </fieldset>
  )
}
