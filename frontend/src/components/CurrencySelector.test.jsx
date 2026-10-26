import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import CurrencySelector from './CurrencySelector'

// US3 currency contract (task T035, written BEFORE the component):
// multi-select of ARS/USD/EUR; an empty selection persists nothing and the
// client shows the ARS default (Q1/FR-011). Optional: never blocks the rest.
afterEach(() => {
  vi.restoreAllMocks()
})

function renderSelector() {
  const onChange = vi.fn()
  render(<CurrencySelector onChange={onChange} />)
  return onChange
}

describe('CurrencySelector (T035)', () => {
  it('la legend no lleva "(opcional" y mantiene el default ARS (T044, FR-023)', () => {
    renderSelector()

    expect(screen.getByText(/monedas \(default ARS\)/i)).toBeInTheDocument()
    expect(screen.queryByText(/opcional/i)).not.toBeInTheDocument()
  })

  it('arranca sin selección y sin error (opcional, Q1)', () => {
    renderSelector()

    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('permite multiselección ARS/USD/EUR y entrega los códigos (Q1/FR-011)', async () => {
    const onChange = renderSelector()
    const user = userEvent.setup()

    await user.click(screen.getByLabelText(/USD/i))
    await user.click(screen.getByLabelText(/EUR/i))
    await user.click(screen.getByLabelText(/ARS/i))

    expect(onChange).toHaveBeenLastCalledWith(['USD', 'EUR', 'ARS'])
  })

  it('selección única entrega una sola moneda', async () => {
    const onChange = renderSelector()
    const user = userEvent.setup()

    await user.click(screen.getByLabelText(/ARS/i))

    expect(onChange).toHaveBeenLastCalledWith(['ARS'])
  })

  it('deseleccionar todo vuelve a vacío → el form no se bloquea (Q1/FR-003)', async () => {
    const onChange = renderSelector()
    const user = userEvent.setup()

    const ars = screen.getByLabelText(/ARS/i)
    await user.click(ars)
    await user.click(ars)

    expect(onChange).toHaveBeenLastCalledWith([])
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })
})
