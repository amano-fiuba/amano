import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../api/expenseClient', () => ({
  EXPENSE_NETWORK_ERROR_MESSAGE:
    'No se pudo guardar el gasto. Intenta nuevamente',
  createExpense: vi.fn(),
}))

import { createExpense } from '../api/expenseClient'
import ExpenseForm, {
  EXPENSE_REUSED_KEY_MESSAGE,
  EXPENSE_SUCCESS_MESSAGE,
  SUCCESS_VISIBLE_MS,
} from './ExpenseForm'

const TODAY = '21/10/2026'

function renderForm(props = {}) {
  return render(<ExpenseForm today={TODAY} initialCurrency="USD" {...props} />)
}

async function completeRequired(user) {
  await user.type(screen.getByLabelText('Monto'), '1.234,56')
  await user.selectOptions(screen.getByLabelText('Categoría'), 'FOOD')
  await user.selectOptions(screen.getByLabelText('Tipo de gasto'), 'ONE_TIME')
  await user.selectOptions(screen.getByLabelText('Medio de pago'), 'CASH')
  await user.type(screen.getByLabelText('Descripción'), 'Almuerzo')
}

function submitButton() {
  return screen.getByRole('button', { name: /guardar gasto|guardando/i })
}

beforeEach(() => {
  createExpense.mockReset()
})

afterEach(() => {
  vi.useRealTimers()
})

describe('ExpenseForm (T029/T030)', () => {
  it('envía el payload normalizado con la clave y muestra el éxito, resetea y permanece (US1-1)', async () => {
    const user = userEvent.setup()
    createExpense.mockResolvedValue({ ok: true, expense: { id: 1 } })
    renderForm()

    await completeRequired(user)
    await user.click(submitButton())

    await waitFor(() => expect(createExpense).toHaveBeenCalledTimes(1))
    const [payload, key] = createExpense.mock.calls[0]
    expect(payload).toMatchObject({
      amount: '1234.56',
      currency: 'USD',
      date: '2026-10-21',
      category: 'FOOD',
      type: 'ONE_TIME',
      paymentMethod: 'CASH',
      description: 'Almuerzo',
    })
    expect(payload.deviceTimeZone).toEqual(expect.any(String))
    expect(key).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/)

    expect(await screen.findByRole('status')).toHaveTextContent(EXPENSE_SUCCESS_MESSAGE)
    expect(screen.getByLabelText('Monto')).toHaveValue('')
    expect(screen.getByLabelText('Descripción')).toHaveValue('')
    expect(screen.getByLabelText('Fecha')).toHaveValue(TODAY)
    expect(screen.getByLabelText('Moneda')).toHaveValue('USD')
    expect(screen.getByLabelText('Categoría')).toHaveValue('')
    expect(screen.getByLabelText('Tipo de gasto')).toHaveValue('')
    expect(screen.getByLabelText('Medio de pago')).toHaveValue('')
    expect(screen.getByRole('button', { name: 'Guardar gasto' })).toBeInTheDocument()
  })

  it('cierra la notificación automáticamente a los 5 s (FR-031)', async () => {
    const user = userEvent.setup()
    createExpense.mockResolvedValue({ ok: true, expense: { id: 1 } })
    renderForm()

    await completeRequired(user)
    vi.useFakeTimers()
    fireEvent.click(submitButton())
    await act(async () => {
      await Promise.resolve()
      await Promise.resolve()
      await Promise.resolve()
    })
    expect(screen.getByRole('status')).toHaveTextContent(EXPENSE_SUCCESS_MESSAGE)

    act(() => {
      vi.advanceTimersByTime(SUCCESS_VISIBLE_MS - 1)
    })
    expect(screen.getByRole('status')).toBeInTheDocument()

    act(() => {
      vi.advanceTimersByTime(1)
    })
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('permite cerrar la notificación manualmente antes de los 5 s (FR-031)', async () => {
    const user = userEvent.setup()
    createExpense.mockResolvedValue({ ok: true, expense: { id: 1 } })
    renderForm()

    await completeRequired(user)
    await user.click(submitButton())
    await screen.findByRole('status')

    await user.click(screen.getByRole('button', { name: 'Cerrar notificación' }))

    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('deshabilita el botón con "Guardando…" mientras la operación está en curso (FR-027)', async () => {
    let resolveRequest
    const user = userEvent.setup()
    createExpense.mockReturnValue(new Promise((resolve) => {
      resolveRequest = resolve
    }))
    renderForm()

    await completeRequired(user)
    await user.click(submitButton())

    const pending = screen.getByRole('button', { name: 'Guardando…' })
    expect(pending).toBeDisabled()

    await act(async () => {
      resolveRequest({ ok: true, expense: { id: 1 } })
      await Promise.resolve()
    })

    expect(await screen.findByRole('status')).toHaveTextContent(EXPENSE_SUCCESS_MESSAGE)
  })

  it('conserva la clave ante un fallo de red y la renueva tras el éxito (FR-027)', async () => {
    const user = userEvent.setup()
    createExpense
      .mockResolvedValueOnce({ ok: false, network: true })
      .mockResolvedValueOnce({ ok: false, network: true })
      .mockResolvedValue({ ok: true, expense: { id: 1 } })
    renderForm()

    await completeRequired(user)
    await user.click(submitButton())
    await screen.findByTestId('expense-error')
    await user.click(submitButton())
    await waitFor(() => expect(createExpense).toHaveBeenCalledTimes(2))

    const firstKey = createExpense.mock.calls[0][1]
    const secondKey = createExpense.mock.calls[1][1]
    expect(secondKey).toBe(firstKey)
    // Los datos se conservan ante el fallo.
    expect(screen.getByLabelText('Monto')).toHaveValue('1.234,56')

    await user.click(submitButton())
    await screen.findByRole('status')
    // La operación exitosa todavía usa la clave conservada; la renovación
    // aplica a la SIGUIENTE operación, ya con el formulario reseteado.
    const successKey = createExpense.mock.calls[2][1]
    expect(successKey).toBe(firstKey)

    await completeRequired(user)
    await user.click(submitButton())
    await waitFor(() => expect(createExpense).toHaveBeenCalledTimes(4))
    expect(createExpense.mock.calls[3][1]).not.toBe(firstKey)
  })

  it('ante 422 conserva la clave e informa que hay que confirmar de nuevo (T029)', async () => {
    const user = userEvent.setup()
    createExpense
      .mockResolvedValueOnce({
        ok: false,
        status: 422,
        reusedKey: true,
        detail: 'La operación ya fue registrada con datos distintos. Reintentá el guardado.',
      })
      .mockResolvedValueOnce({ ok: false, network: true })
    renderForm()

    await completeRequired(user)
    await user.click(submitButton())

    expect(await screen.findByTestId('expense-error')).toHaveTextContent(
      EXPENSE_REUSED_KEY_MESSAGE,
    )
    await user.click(submitButton())
    await waitFor(() => expect(createExpense).toHaveBeenCalledTimes(2))
    expect(createExpense.mock.calls[1][1]).toBe(createExpense.mock.calls[0][1])
    expect(screen.getByLabelText('Categoría')).toHaveValue('FOOD')
  })

  it('muestra el error de servidor junto al campo sin limpiar los datos (T029)', async () => {
    const user = userEvent.setup()
    createExpense.mockResolvedValue({
      ok: false,
      status: 400,
      detail: 'La solicitud contiene campos inválidos',
      fields: { amount: 'El monto no puede tener más de dos decimales' },
    })
    renderForm()

    await completeRequired(user)
    await user.click(submitButton())

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'El monto no puede tener más de dos decimales',
    )
    expect(screen.getByLabelText('Monto')).toHaveValue('1.234,56')
    expect(screen.getByLabelText('Descripción')).toHaveValue('Almuerzo')
  })
})
