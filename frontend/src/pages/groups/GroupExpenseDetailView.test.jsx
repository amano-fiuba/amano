import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../../api/groupExpensesClient', async (importOriginal) => ({
  ...await importOriginal(),
  getGroupExpense: vi.fn(),
}))

const setUser = vi.fn()
vi.mock('../../auth/AuthContext', () => ({
  useAuth: () => ({ setUser }),
}))

import {
  GROUP_EXPENSE_LOAD_ERROR_MESSAGE,
  getGroupExpense,
} from '../../api/groupExpensesClient'
import GroupExpenseDetailView, { EXPENSE_NOT_FOUND_MESSAGE } from './GroupExpenseDetailView'

// 017 T037 (US3): detail of a group expense with its debts and their states.

const ANA = { userId: 7, name: 'Ana López' }

const EXPENSE = {
  id: 12,
  groupId: 3,
  amount: '10000.00',
  currency: 'ARS',
  date: '2026-11-05',
  description: 'Súper',
  payer: ANA,
  createdBy: ANA,
  createdAt: '2026-11-05T15:20:00Z',
  shares: [
    { position: 0, type: 'REGISTERED', userId: 7, simulatedProfileId: null, name: 'Ana López', amount: '3333.34', isPayer: true },
    { position: 1, type: 'REGISTERED', userId: 15, simulatedProfileId: null, name: 'Juan Pérez', amount: '3333.33', isPayer: false },
    { position: 2, type: 'SIMULATED', userId: null, simulatedProfileId: null, name: 'Abuela', amount: '3333.33', isPayer: false },
  ],
  debts: [
    {
      id: 31,
      debtor: { type: 'REGISTERED', userId: 15, simulatedProfileId: null, name: 'Juan Pérez' },
      creditor: ANA,
      amount: '3333.33',
      paidAmount: '1000.00',
      outstanding: '2333.33',
      currency: 'ARS',
      status: 'PARTIALLY_PAID',
    },
    {
      id: 32,
      debtor: { type: 'SIMULATED', userId: null, simulatedProfileId: null, name: 'Abuela' },
      creditor: ANA,
      amount: '3333.33',
      paidAmount: '0.00',
      outstanding: '3333.33',
      currency: 'ARS',
      status: 'PENDING',
    },
  ],
}

function renderView() {
  return render(
    <MemoryRouter initialEntries={['/groups/3/expenses/12']}>
      <Routes>
        <Route path="/groups/:id/expenses/:expenseId" element={<GroupExpenseDetailView />} />
      </Routes>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  getGroupExpense.mockReset()
  setUser.mockReset()
})

describe('GroupExpenseDetailView (017)', () => {
  it('muestra el total, el pagador, las partes en orden y cada deuda con su estado', async () => {
    getGroupExpense.mockResolvedValue({ ok: true, expense: EXPENSE })
    renderView()
    expect(screen.getByRole('status')).toHaveTextContent('Cargando…')

    const header = await screen.findByTestId('group-expense-detail')
    expect(header).toHaveTextContent('Súper')
    expect(header).toHaveTextContent('ARS $ 10.000,00')
    expect(header).toHaveTextContent('05/11/2026 · Pagó Ana López')
    expect(getGroupExpense).toHaveBeenCalledWith('3', '12')

    const shares = screen.getAllByTestId('share-row')
    expect(shares.map((row) => row.textContent)).toEqual([
      'Ana López (pagó)$ 3.333,34',
      'Juan Pérez$ 3.333,33',
      'Abuela$ 3.333,33',
    ])

    const debts = screen.getAllByTestId('debt-row')
    expect(debts[0]).toHaveTextContent('Juan Pérez le debe a Ana López')
    expect(within(debts[0]).getByTestId('debt-status')).toHaveTextContent('Pago parcial · faltan $ 2.333,33')
    expect(debts[1]).toHaveTextContent('Abuela le debe a Ana López')
    expect(within(debts[1]).getByTestId('debt-status')).toHaveTextContent('Pendiente')
    expect(screen.getByRole('link', { name: 'Volver al grupo' })).toHaveAttribute('href', '/groups/3')
  })

  it('cada deuda que la persona puede pagar lleva a "Registrar pago" (018 FR-017)', async () => {
    getGroupExpense.mockResolvedValue({
      ok: true,
      expense: {
        ...EXPENSE,
        debts: [{ ...EXPENSE.debts[0], canRegisterPayment: true }, { ...EXPENSE.debts[1], canRegisterPayment: false }],
      },
    })
    renderView()
    const debts = await screen.findAllByTestId('debt-row')
    expect(within(debts[0]).getByRole('link', { name: 'Registrar pago' }))
      .toHaveAttribute('href', '/groups/3/expenses/12/debts/31/payment')
    expect(within(debts[1]).queryByRole('link', { name: 'Registrar pago' })).not.toBeInTheDocument()
  })

  it('un gasto sin descripción y una deuda pagada', async () => {
    getGroupExpense.mockResolvedValue({
      ok: true,
      expense: {
        ...EXPENSE,
        description: undefined,
        payer: null,
        shares: undefined,
        debts: [{ ...EXPENSE.debts[0], status: 'PAID', paidAmount: '3333.33', outstanding: '0.00' }],
      },
    })
    renderView()
    expect(await screen.findByRole('heading', { name: 'Gasto sin descripción' })).toBeInTheDocument()
    expect(screen.getByTestId('group-expense-detail')).toHaveTextContent('Pagó alguien')
    expect(screen.queryByTestId('share-row')).not.toBeInTheDocument()
    expect(screen.getByTestId('debt-status')).toHaveTextContent('Pagada')
  })

  it('404 muestra el mensaje neutral sin reintento', async () => {
    getGroupExpense.mockResolvedValue({ ok: false, status: 404 })
    renderView()
    const alert = await screen.findByTestId('group-expense-detail-error')
    expect(alert).toHaveTextContent(EXPENSE_NOT_FOUND_MESSAGE)
    expect(within(alert).queryByRole('button')).not.toBeInTheDocument()
  })

  it('un error de red permite reintentar', async () => {
    const user = userEvent.setup()
    getGroupExpense
      .mockResolvedValueOnce({ ok: false, network: true })
      .mockResolvedValueOnce({ ok: true, expense: EXPENSE })
    renderView()
    const alert = await screen.findByTestId('group-expense-detail-error')
    expect(alert).toHaveTextContent(GROUP_EXPENSE_LOAD_ERROR_MESSAGE)

    await user.click(within(alert).getByRole('button', { name: 'Reintentar' }))
    expect(await screen.findByTestId('group-expense-detail')).toBeInTheDocument()
  })

  it('401 cierra la sesión', async () => {
    getGroupExpense.mockResolvedValue({ ok: false, status: 401 })
    renderView()
    await waitFor(() => expect(setUser).toHaveBeenCalledWith(null))
  })
})
