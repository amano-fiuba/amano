import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router'

vi.mock('../api/expenseClient', async (importOriginal) => ({
  ...await importOriginal(),
  listExpenseHistory: vi.fn(),
}))
vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ setUser: vi.fn() }) }))

import { listExpenseHistory } from '../api/expenseClient'
import ActivityPage from './ActivityPage'

function renderPage() {
  return render(
    <MemoryRouter>
      <ActivityPage />
    </MemoryRouter>,
  )
}

beforeEach(() => {
  listExpenseHistory.mockReset()
})

describe('ActivityPage', () => {
  it('lista los gastos con descripción, fecha y monto', async () => {
    listExpenseHistory.mockResolvedValue({
      ok: true,
      expenses: [
        { id: 1, description: 'Pizza', category: 'FOOD', date: '2026-11-02', currency: 'ARS', amount: '1500.00' },
      ],
    })

    renderPage()

    expect(await screen.findByText('Pizza')).toBeInTheDocument()
    expect(screen.getByText(/ARS/)).toBeInTheDocument()
  })

  it('muestra la conversión a las monedas preferidas debajo del importe original', async () => {
    listExpenseHistory.mockResolvedValue({
      ok: true,
      expenses: [
        {
          id: 1,
          description: 'Hotel',
          category: 'HOUSING',
          date: '2026-11-02',
          currency: 'USD',
          amount: '100.00',
          conversions: [
            { currency: 'ARS', amount: '150000.00' },
            { currency: 'EUR', amount: '95.00' },
          ],
        },
      ],
    })

    renderPage()

    expect(await screen.findByText('Hotel')).toBeInTheDocument()
    expect(screen.getByText('≈ ARS 150.000,00')).toBeInTheDocument()
    expect(screen.getByText('≈ EUR 95,00')).toBeInTheDocument()
  })

  it('sin gastos ofrece registrar el primero', async () => {
    listExpenseHistory.mockResolvedValue({ ok: true, expenses: [] })

    renderPage()

    expect(await screen.findByRole('link', { name: 'Registrar un gasto' })).toHaveAttribute(
      'href',
      '/expenses/new',
    )
  })

  it('informa el error de conexión', async () => {
    listExpenseHistory.mockResolvedValue({ ok: false, network: true })

    renderPage()

    expect(await screen.findByRole('alert')).toBeInTheDocument()
  })
})
