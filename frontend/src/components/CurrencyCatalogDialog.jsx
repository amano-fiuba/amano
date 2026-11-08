import { useEffect, useId, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { listCurrencies } from '../api/currencyClient'
import { currencyLabel } from '../validation/expenseCatalog'
import { normalizeText } from '../validation/normalizeText'
import './CurrencyCatalogDialog.scss'

export const CATALOG_TITLE = 'Elegir otra moneda'
export const CATALOG_ERROR_MESSAGE =
  'No se pudo obtener el catálogo de monedas del BCRA. Reintentá en unos minutos.'
export const CATALOG_EMPTY_MESSAGE = 'No hay monedas que coincidan con la búsqueda.'

/**
 * "Ver más" dialog (027, FR-012/FR-013): the BCRA catalog from GET
 * /api/currencies, searchable by code or name without accents. Choosing does not
 * touch the user's preferences: it only hands the code back with `onSelect`.
 */
export default function CurrencyCatalogDialog({ initialCode = null, onSelect, onClose }) {
  const titleId = useId()
  const [state, setState] = useState({ status: 'loading', currencies: [] })
  const [attempt, setAttempt] = useState(0)
  const [query, setQuery] = useState('')
  const [chosen, setChosen] = useState(initialCode)
  const searchRef = useRef(null)

  useEffect(() => {
    let cancelled = false
    listCurrencies().then((result) => {
      if (cancelled) {
        return
      }
      setState(result.ok ? { status: 'ready', currencies: result.currencies } : { status: 'error', currencies: [] })
    })
    return () => {
      cancelled = true
    }
  }, [attempt])

  useEffect(() => {
    searchRef.current?.focus()
  }, [])

  const retry = () => {
    setState({ status: 'loading', currencies: [] })
    setAttempt((value) => value + 1)
  }

  const handleKeyDown = (event) => {
    if (event.key === 'Escape') {
      // The dialog may live inside a menu or form that also listens to Escape.
      event.stopPropagation()
      onClose()
    }
  }

  const term = normalizeText(query.trim())
  const visible = state.currencies.filter((entry) =>
    [entry.code, entry.bcraName, currencyLabel(entry.code)].some((text) => normalizeText(text).includes(term)),
  )

  // Portal: rendered inside a form field it would inherit the field's input styles.
  return createPortal(
    <div className="currency-dialog__backdrop">
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        className="currency-dialog"
        onKeyDown={handleKeyDown}
      >
        <h3 id={titleId}>{CATALOG_TITLE}</h3>
        <div className="field">
          <label htmlFor={`${titleId}-search`}>Buscar por código o nombre</label>
          <input
            ref={searchRef}
            id={`${titleId}-search`}
            type="search"
            autoComplete="off"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
          />
        </div>

        {state.status === 'loading' && (
          <p role="status" className="currency-dialog__status">
            Cargando monedas…
          </p>
        )}
        {state.status === 'error' && (
          <div className="currency-dialog__status">
            <p role="alert">{CATALOG_ERROR_MESSAGE}</p>
            <button type="button" className="currency-dialog__retry" onClick={retry}>
              Reintentar
            </button>
          </div>
        )}
        {state.status === 'ready' && visible.length === 0 && (
          <p className="currency-dialog__status">{CATALOG_EMPTY_MESSAGE}</p>
        )}
        {state.status === 'ready' && visible.length > 0 && (
          <div role="radiogroup" aria-label="Monedas del BCRA" className="currency-dialog__list">
            {visible.map((entry) => (
              <label key={entry.code} className={chosen === entry.code ? 'currency-dialog__option is-chosen' : 'currency-dialog__option'}>
                <input
                  type="radio"
                  name={`${titleId}-currency`}
                  value={entry.code}
                  checked={chosen === entry.code}
                  onChange={() => setChosen(entry.code)}
                  aria-label={`${currencyLabel(entry.code, entry.bcraName)} (${entry.code})`}
                />
                <span className="currency-dialog__name" aria-hidden="true">{currencyLabel(entry.code, entry.bcraName)}</span>
                <span className="currency-dialog__code" aria-hidden="true">{entry.code}</span>
              </label>
            ))}
          </div>
        )}

        <div className="currency-dialog__actions">
          <button type="button" className="currency-dialog__cancel" onClick={onClose}>
            Cancelar
          </button>
          <button type="button" disabled={!chosen} onClick={() => onSelect(chosen)}>
            Elegir
          </button>
        </div>
      </div>
    </div>,
    document.body,
  )
}
