import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../api/expenseClient', () => ({
  EXPENSE_NETWORK_ERROR_MESSAGE:
    'No se pudo guardar el gasto. Intenta nuevamente',
  createExpense: vi.fn(),
}))

import { createExpense } from '../api/expenseClient'
import { SNACKBAR_VISIBLE_MS } from './Snackbar'
import ExpenseForm, {
  EXPENSE_REUSED_KEY_MESSAGE,
  FREQUENCY_REQUIRED_MESSAGE,
  EXPENSE_SESSION_EXPIRED_MESSAGE,
  EXPENSE_SUCCESS_MESSAGE,
} from './ExpenseForm'
import {
  AMOUNT_DECIMALS,
  AMOUNT_FORMAT,
  AMOUNT_POSITIVE,
  AMOUNT_RANGE,
  AMOUNT_REQUIRED,
  DATE_FORMAT,
  DATE_FUTURE,
  DATE_INVALID,
  DATE_REQUIRED,
  DESCRIPTION_LENGTH,
} from '../validation/expenseValidation'

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
      vi.advanceTimersByTime(SNACKBAR_VISIBLE_MS - 1)
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

  it('ante 422 renueva la clave, conserva los datos y habilita un nuevo confirmar (T043)', async () => {
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
    expect(screen.getByLabelText('Monto')).toHaveValue('1.234,56')
    expect(screen.getByLabelText('Categoría')).toHaveValue('FOOD')

    await user.click(submitButton())
    await waitFor(() => expect(createExpense).toHaveBeenCalledTimes(2))
    expect(createExpense.mock.calls[1][1]).not.toBe(createExpense.mock.calls[0][1])
  })

  it('un doble clic durante la operación envía una sola vez (T044)', async () => {
    let resolveRequest
    const user = userEvent.setup()
    createExpense.mockReturnValue(new Promise((resolve) => {
      resolveRequest = resolve
    }))
    renderForm()
    await fillValidRequired(user)

    await user.dblClick(submitButton())

    expect(createExpense).toHaveBeenCalledTimes(1)
    expect(screen.getByRole('button', { name: 'Guardando…' })).toBeDisabled()
    await act(async () => {
      resolveRequest({ ok: true, expense: { id: 1 } })
      await Promise.resolve()
    })
    expect(await screen.findByRole('status')).toBeInTheDocument()
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


async function fillValidRequired(user) {
  await user.type(screen.getByLabelText('Monto'), '10,10')
  await user.selectOptions(screen.getByLabelText('Categoría'), 'FOOD')
  await user.selectOptions(screen.getByLabelText('Tipo de gasto'), 'ONE_TIME')
  await user.selectOptions(screen.getByLabelText('Medio de pago'), 'CASH')
}

function changeValue(label, value) {
  fireEvent.change(screen.getByLabelText(label), { target: { value } })
}

const invalidCases = [
  {
    name: 'monto vacío',
    field: 'Monto',
    message: AMOUNT_REQUIRED,
    apply: () => changeValue('Monto', ''),
  },
  {
    name: 'monto con formato inválido',
    field: 'Monto',
    message: AMOUNT_FORMAT,
    apply: () => changeValue('Monto', 'abc'),
  },
  {
    name: 'monto negativo',
    field: 'Monto',
    message: AMOUNT_FORMAT,
    apply: () => changeValue('Monto', '-10'),
  },
  {
    name: 'monto con punto decimal',
    field: 'Monto',
    message: AMOUNT_FORMAT,
    apply: () => changeValue('Monto', '1234.56'),
  },
  {
    name: 'monto con tres decimales',
    field: 'Monto',
    message: AMOUNT_DECIMALS,
    apply: () => changeValue('Monto', '1,234'),
  },
  {
    name: 'monto cero',
    field: 'Monto',
    message: AMOUNT_POSITIVE,
    apply: () => changeValue('Monto', '0,00'),
  },
  {
    name: 'monto fuera de rango',
    field: 'Monto',
    message: AMOUNT_RANGE,
    apply: () => changeValue('Monto', '1.000.000.000,00'),
  },
  {
    name: 'moneda vacía',
    field: 'Moneda',
    message: 'La moneda es obligatoria',
    apply: () => changeValue('Moneda', ''),
  },
  {
    name: 'fecha vacía',
    field: 'Fecha',
    message: DATE_REQUIRED,
    apply: () => changeValue('Fecha', ''),
  },
  {
    name: 'fecha con formato inválido',
    field: 'Fecha',
    message: DATE_FORMAT,
    apply: () => changeValue('Fecha', '2026-10-21'),
  },
  {
    name: 'fecha inexistente',
    field: 'Fecha',
    message: DATE_INVALID,
    apply: () => changeValue('Fecha', '31/02/2026'),
  },
  {
    name: 'fecha futura',
    field: 'Fecha',
    message: DATE_FUTURE,
    apply: () => changeValue('Fecha', '22/10/2026'),
  },
  {
    name: 'categoría vacía',
    field: 'Categoría',
    message: 'La categoría es obligatoria',
    apply: () => changeValue('Categoría', ''),
  },
  {
    name: 'tipo vacío',
    field: 'Tipo de gasto',
    message: 'El tipo de gasto es obligatorio',
    apply: () => changeValue('Tipo de gasto', ''),
  },
  {
    name: 'medio de pago vacío',
    field: 'Medio de pago',
    message: 'El medio de pago es obligatorio',
    apply: () => changeValue('Medio de pago', ''),
  },
  {
    name: 'descripción de 201 caracteres',
    field: 'Descripción',
    message: DESCRIPTION_LENGTH,
    apply: () => changeValue('Descripción', 'a'.repeat(201)),
  },
]

describe('ExpenseForm validación (T034/T036)', () => {
  it.each(invalidCases)('$name bloquea el envío y asocia el error al campo', async ({
    field,
    message,
    apply,
  }) => {
    const user = userEvent.setup()
    renderForm()
    await fillValidRequired(user)
    apply()

    await user.click(submitButton())

    const alert = await screen.findByText(message)
    expect(createExpense).not.toHaveBeenCalled()
    const control = screen.getByLabelText(field)
    expect(control).toHaveAttribute('aria-invalid', 'true')
    expect(control).toHaveAttribute('aria-describedby', alert.id)
    if (field === 'Moneda') {
      expect(screen.getByLabelText('Monto')).toHaveValue('10,10')
    } else {
      expect(screen.getByLabelText('Moneda')).toHaveValue('USD')
    }
    expect(document.body.textContent).not.toMatch(
      /exception|stacktrace|java\.|sql|token|cookie|password|@/i,
    )
  })

  it('revalida al corregir el campo y permite enviar', async () => {
    const user = userEvent.setup()
    createExpense.mockResolvedValue({ ok: true, expense: { id: 1 } })
    renderForm()
    await fillValidRequired(user)
    changeValue('Categoría', '')

    await user.click(submitButton())
    expect(await screen.findByText('La categoría es obligatoria')).toBeInTheDocument()
    expect(createExpense).not.toHaveBeenCalled()

    await user.selectOptions(screen.getByLabelText('Categoría'), 'FOOD')
    await waitFor(() => {
      expect(screen.queryByText('La categoría es obligatoria')).not.toBeInTheDocument()
    })

    await user.click(submitButton())
    await waitFor(() => expect(createExpense).toHaveBeenCalledTimes(1))
  })

  it('acepta los límites válidos de monto, fecha y descripción', async () => {
    const user = userEvent.setup()
    createExpense.mockResolvedValue({ ok: true, expense: { id: 1 } })
    renderForm()
    await fillValidRequired(user)
    changeValue('Monto', '999.999.999,99')
    changeValue('Fecha', TODAY)
    changeValue('Descripción', 'a'.repeat(200))

    await user.click(submitButton())

    await waitFor(() => expect(createExpense).toHaveBeenCalledTimes(1))
    const [payload] = createExpense.mock.calls[0]
    expect(payload.amount).toBe('999999999.99')
    expect(payload.date).toBe('2026-10-21')
    expect(payload.description).toHaveLength(200)
  })

  it('los errores mostrados no exponen detalles internos (SC-006)', async () => {
    const user = userEvent.setup()
    createExpense.mockResolvedValue({
      ok: false,
      status: 400,
      detail: 'La solicitud contiene campos inválidos',
      fields: { amount: 'El monto debe ser mayor a cero' },
    })
    renderForm()
    await fillValidRequired(user)
    await user.click(submitButton())
    await screen.findByText('El monto debe ser mayor a cero')

    expect(document.body).not.toHaveTextContent(
      /exception|stacktrace|java\.|sql|token|cookie|password/i,
    )
  })
})

describe('ExpenseForm errores del backend (T035/T037)', () => {
  const fieldLabels = {
    amount: 'Monto',
    currency: 'Moneda',
    date: 'Fecha',
    category: 'Categoría',
    type: 'Tipo de gasto',
    paymentMethod: 'Medio de pago',
    description: 'Descripción',
  }

  it.each(Object.entries(fieldLabels))(
    'muestra el 400 de %s junto a su campo',
    async (field, label) => {
      const user = userEvent.setup()
      const message = `Error de servidor para ${field}`
      createExpense.mockResolvedValue({
        ok: false,
        status: 400,
        detail: 'La solicitud contiene campos inválidos',
        fields: { [field]: message },
      })
      renderForm()
      await fillValidRequired(user)
      await user.click(submitButton())

      expect(await screen.findByText(message)).toBeInTheDocument()
      expect(screen.getByLabelText(label)).toHaveAttribute('aria-invalid', 'true')
    },
  )

  it('usa el detalle genérico cuando el 400 no trae campos', async () => {
    const user = userEvent.setup()
    createExpense.mockResolvedValue({
      ok: false,
      status: 400,
      detail: 'La solicitud contiene campos inválidos',
      fields: {},
    })
    renderForm()
    await fillValidRequired(user)
    await user.click(submitButton())

    expect(await screen.findByTestId('expense-error')).toHaveTextContent(
      'La solicitud contiene campos inválidos',
    )
  })
})

describe('ExpenseForm resiliencia (T045/T046)', () => {
  it('ante 401 informa, conserva los datos y ofrece el login existente', async () => {
    const user = userEvent.setup()
    createExpense.mockResolvedValue({ ok: false, status: 401, fields: {}, detail: '' })
    renderForm()
    await fillValidRequired(user)
    await user.click(submitButton())

    expect(await screen.findByTestId('expense-error')).toHaveTextContent(
      EXPENSE_SESSION_EXPIRED_MESSAGE,
    )
    expect(screen.getByRole('link', { name: 'Iniciar sesión' })).toHaveAttribute(
      'href',
      '/login',
    )
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Monto')).toHaveValue('10,10')
    expect(screen.getByLabelText('Categoría')).toHaveValue('FOOD')
    expect(document.body.textContent).not.toMatch(
      /exception|stacktrace|java\.|sql|token|cookie|password|@/i,
    )
  })

  it('ante falla de red/5xx muestra el mensaje fijo, sin éxito y conserva los datos', async () => {
    const user = userEvent.setup()
    createExpense.mockResolvedValue({ ok: false, network: true })
    renderForm()
    await fillValidRequired(user)
    await user.click(submitButton())

    expect(await screen.findByTestId('expense-error')).toHaveTextContent(
      'No se pudo guardar el gasto. Intenta nuevamente',
    )
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Monto')).toHaveValue('10,10')
    expect(screen.getByLabelText('Categoría')).toHaveValue('FOOD')
    expect(document.body.textContent).not.toMatch(
      /exception|stacktrace|java\.|sql|token|cookie|password|@/i,
    )
  })
})

describe('ExpenseForm accesibilidad y móvil (T047)', () => {
  it('asocia todas las etiquetas y permite recorrer el formulario con teclado', async () => {
    const user = userEvent.setup()
    renderForm()

    const order = [
      'Monto',
      'Moneda',
      'Fecha',
      'Categoría',
      'Tipo de gasto',
      'Medio de pago',
      'Descripción',
    ]
    for (const label of order) {
      await user.tab()
      expect(screen.getByLabelText(label)).toHaveFocus()
    }
    await user.tab()
    expect(submitButton()).toHaveFocus()
  })

  it('no desborda el viewport móvil y mantiene los controles accesibles', () => {
    const originalWidth = window.innerWidth
    Object.defineProperty(window, 'innerWidth', { value: 375, configurable: true })
    try {
      renderForm()
      for (const element of document.querySelectorAll('input, select, button')) {
        expect(element.getBoundingClientRect().width).toBeLessThanOrEqual(375)
        expect(element).toBeVisible()
      }
      expect(document.body.scrollWidth).toBeLessThanOrEqual(375)
    } finally {
      Object.defineProperty(window, 'innerWidth', { value: originalWidth, configurable: true })
    }
  })
})

describe('ExpenseForm frecuencia de gastos recurrentes (009, FR-001/FR-002)', () => {
  async function completeRecurring(user) {
    await user.type(screen.getByLabelText('Monto'), '250.000,00')
    await user.selectOptions(screen.getByLabelText('Categoría'), 'HOUSING')
    await user.selectOptions(screen.getByLabelText('Tipo de gasto'), 'RECURRING')
    await user.selectOptions(screen.getByLabelText('Medio de pago'), 'TRANSFER')
  }

  it('muestra "Frecuencia" solo cuando el tipo es Recurrente', async () => {
    const user = userEvent.setup()
    renderForm()

    expect(screen.queryByLabelText('Frecuencia')).not.toBeInTheDocument()
    await user.selectOptions(screen.getByLabelText('Tipo de gasto'), 'RECURRING')
    const frequency = screen.getByLabelText('Frecuencia')
    expect(
      Array.from(frequency.options)
        .map((option) => option.textContent)
        .slice(1),
    ).toEqual(['Semanal', 'Mensual', 'Anual'])

    await user.selectOptions(screen.getByLabelText('Tipo de gasto'), 'ONE_TIME')
    expect(screen.queryByLabelText('Frecuencia')).not.toBeInTheDocument()
  })

  it('exige la frecuencia de un gasto recurrente y no envía nada sin ella', async () => {
    const user = userEvent.setup()
    renderForm()

    await completeRecurring(user)
    await user.click(submitButton())

    expect(await screen.findByText(FREQUENCY_REQUIRED_MESSAGE)).toBeInTheDocument()
    expect(createExpense).not.toHaveBeenCalled()
  })

  it('envía la frecuencia elegida y la limpia tras el éxito', async () => {
    const user = userEvent.setup()
    createExpense.mockResolvedValue({ ok: true, expense: { id: 81 } })
    renderForm()

    await completeRecurring(user)
    await user.selectOptions(screen.getByLabelText('Frecuencia'), 'MONTHLY')
    await user.click(submitButton())

    await waitFor(() => expect(createExpense).toHaveBeenCalledTimes(1))
    expect(createExpense.mock.calls[0][0]).toMatchObject({
      amount: '250000.00',
      type: 'RECURRING',
      frequency: 'MONTHLY',
    })
    expect(await screen.findByRole('status')).toHaveTextContent(EXPENSE_SUCCESS_MESSAGE)
    expect(screen.queryByLabelText('Frecuencia')).not.toBeInTheDocument()
  })

  it('no envía la frecuencia de un gasto único aunque se haya elegido antes', async () => {
    const user = userEvent.setup()
    createExpense.mockResolvedValue({ ok: true, expense: { id: 82 } })
    renderForm()

    await completeRecurring(user)
    await user.selectOptions(screen.getByLabelText('Frecuencia'), 'WEEKLY')
    await user.selectOptions(screen.getByLabelText('Tipo de gasto'), 'ONE_TIME')
    await user.click(submitButton())

    await waitFor(() => expect(createExpense).toHaveBeenCalledTimes(1))
    expect(createExpense.mock.calls[0][0]).not.toHaveProperty('frequency')
    expect(createExpense.mock.calls[0][0].type).toBe('ONE_TIME')
  })

  it('muestra el error de servidor de la frecuencia junto al campo', async () => {
    const user = userEvent.setup()
    createExpense.mockResolvedValue({
      ok: false,
      status: 400,
      detail: 'La solicitud contiene campos inválidos',
      fields: { frequency: 'La frecuencia no es válida' },
    })
    renderForm()

    await completeRecurring(user)
    await user.selectOptions(screen.getByLabelText('Frecuencia'), 'YEARLY')
    await user.click(submitButton())

    expect(await screen.findByText('La frecuencia no es válida')).toBeInTheDocument()
    expect(screen.getByLabelText('Frecuencia')).toHaveValue('YEARLY')
  })
})

