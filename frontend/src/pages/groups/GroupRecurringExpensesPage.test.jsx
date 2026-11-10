import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../../api/groupsClient', async (importOriginal) => ({
  ...await importOriginal(),
  listGroupRecurringExpenses: vi.fn(),
  updateGroupRecurringExpense: vi.fn(),
}))

import {
  listGroupRecurringExpenses,
  updateGroupRecurringExpense,
} from '../../api/groupsClient'
import { SnackbarProvider } from '../../components/Snackbar'
import GroupRecurringExpensesPage, {
  EMPTY_MESSAGE,
  LOAD_ERROR_MESSAGE,
  PAUSED_MESSAGE,
  RESUMED_MESSAGE,
  FREQUENCY_MESSAGE,
} from './GroupRecurringExpensesPage'

const SERIES = {
  id: 7,
  amount: '50000.00',
  currency: 'ARS',
  date: '2026-11-02',
  category: 'HOUSING',
  type: 'RECURRING',
  paymentMethod: 'TRANSFER',
  description: 'Alquiler',
  frequency: 'WEEKLY',
  status: 'ACTIVE',
  nextOccurrenceDate: '2026-11-16',
}

function renderPage() {
  render(
    <MemoryRouter initialEntries={['/groups/3/recurring-expenses']}>
      <SnackbarProvider>
        <Routes>
          <Route path="/groups/:id/recurring-expenses" element={<GroupRecurringExpensesPage />} />
        </Routes>
      </SnackbarProvider>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  listGroupRecurringExpenses.mockReset()
  updateGroupRecurringExpense.mockReset()
  listGroupRecurringExpenses.mockResolvedValue({ ok: true, series: [SERIES] })
  updateGroupRecurringExpense.mockImplementation(async (groupId, seriesId, payload) => ({
    ok: true,
    series: { ...SERIES, ...payload },
  }))
})

describe('GroupRecurringExpensesPage (028)', () => {
  it('lista las series del grupo con su estado, frecuencia y fechas', async () => {
    renderPage()

    expect(await screen.findByText('Alquiler')).toBeInTheDocument()
    expect(screen.getByText('Activo')).toBeInTheDocument()
    expect(screen.getByText(/50\.000,00 ARS/)).toBeInTheDocument()
    expect(screen.getByText(/Vivienda/)).toBeInTheDocument()
    expect(screen.getByText(/Desde 02\/11\/2026 · Próxima fecha: 16\/11\/2026/)).toBeInTheDocument()
    expect(screen.getByLabelText('Frecuencia de Alquiler')).toHaveValue('WEEKLY')
  })

  it('pausa una serie activa', async () => {
    renderPage()

    fireEvent.click(await screen.findByRole('button', { name: 'Pausar' }))

    await waitFor(() => expect(updateGroupRecurringExpense).toHaveBeenCalledWith(
      '3', 7, { status: 'PAUSED' },
    ))
    expect(await screen.findByRole('status')).toHaveTextContent(PAUSED_MESSAGE)
    expect(screen.getByText('Pausado')).toBeInTheDocument()
  })

  it('reactiva una serie pausada', async () => {
    listGroupRecurringExpenses.mockResolvedValue({
      ok: true,
      series: [{ ...SERIES, status: 'PAUSED' }],
    })
    renderPage()

    fireEvent.click(await screen.findByRole('button', { name: 'Reactivar' }))

    await waitFor(() => expect(updateGroupRecurringExpense).toHaveBeenCalledWith(
      '3', 7, { status: 'ACTIVE' },
    ))
    expect(await screen.findByRole('status')).toHaveTextContent(RESUMED_MESSAGE)
  })

  it('cambia la frecuencia de la serie', async () => {
    updateGroupRecurringExpense.mockResolvedValue({
      ok: true,
      series: { ...SERIES, frequency: 'MONTHLY', nextOccurrenceDate: '2026-12-02' },
    })
    renderPage()

    fireEvent.change(await screen.findByLabelText('Frecuencia de Alquiler'), {
      target: { value: 'MONTHLY' },
    })

    await waitFor(() => expect(updateGroupRecurringExpense).toHaveBeenCalledWith(
      '3', 7, { frequency: 'MONTHLY' },
    ))
    expect(await screen.findByRole('status')).toHaveTextContent(FREQUENCY_MESSAGE)
    expect(screen.getByText(/02\/12\/2026/)).toBeInTheDocument()
  })

  it('muestra el estado vacío', async () => {
    listGroupRecurringExpenses.mockResolvedValue({ ok: true, series: [] })
    renderPage()

    expect(await screen.findByTestId('group-recurring-empty')).toHaveTextContent(EMPTY_MESSAGE)
  })

  it('muestra el error de carga y permite reintentar', async () => {
    listGroupRecurringExpenses
      .mockResolvedValueOnce({ ok: false, network: true })
      .mockResolvedValueOnce({ ok: true, series: [SERIES] })
    renderPage()

    expect(await screen.findByText(LOAD_ERROR_MESSAGE)).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }))

    expect(await screen.findByText('Alquiler')).toBeInTheDocument()
  })
})
