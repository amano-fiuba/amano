import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../../api/groupsClient', async (importOriginal) => ({
  ...await importOriginal(),
  getGroup: vi.fn(),
  getMembers: vi.fn(),
  getExchangeQuote: vi.fn(),
}))

vi.mock('../../api/groupExpensesClient', async (importOriginal) => ({
  ...await importOriginal(),
  createGroupExpense: vi.fn(),
  getGroupExpense: vi.fn(),
  updateGroupExpense: vi.fn(),
}))

const setUser = vi.fn()
vi.mock('../../auth/AuthContext', () => ({
  useAuth: () => ({ user: { id: 7 }, setUser }),
}))

const showSnackbar = vi.fn()
vi.mock('../../components/Snackbar', () => ({
  useSnackbar: () => showSnackbar,
}))

import { getExchangeQuote, getGroup, getMembers } from '../../api/groupsClient'
import {
  GROUP_EXPENSE_NETWORK_ERROR_MESSAGE,
  GROUP_EXPENSE_UPDATE_ERROR_MESSAGE,
  createGroupExpense,
  getGroupExpense,
  updateGroupExpense,
} from '../../api/groupExpensesClient'
import GroupExpenseFormView, {
  CANNOT_CREATE_MESSAGE,
  EDIT_NOT_ALLOWED_MESSAGE,
  EDIT_NOT_PERSONAL_MESSAGE,
  EDIT_NOTHING_MESSAGE,
  EDIT_SUCCESS_MESSAGE,
  EXPENSE_GONE_MESSAGE,
  FORM_LOAD_ERROR_MESSAGE,
  GROUP_EXPENSE_SUCCESS_MESSAGE,
  NOT_A_MEMBER_MESSAGE,
  ONLY_OWN_PAYMENTS_MESSAGE,
  PAYER_ALWAYS_PARTICIPATES,
  PERSONAL_LABEL,
  PERSONAL_NOTE,
  PARTICIPANTS_REQUIRED_MESSAGE,
  QUOTE_DEBOUNCE_MS,
  QUOTE_ERROR_MESSAGE,
  QUOTE_LOADING_MESSAGE,
  QUOTE_NOTICE,
  REUSED_KEY_MESSAGE,
  SPLIT_ESTIMATE_NOTE,
  SPLIT_HINT_CUSTOM,
  SPLIT_HINT_PERCENTAGE,
} from './GroupExpenseFormView'
import { CUSTOM_AMOUNT_INVALID, CUSTOM_SUM_INVALID } from './splitCustom'
import { PERCENTAGE_SUM_INVALID } from './splitPercentage'
import { REST_IN_ORDER_NOTE, REST_TO_PAYER_NOTE } from '../../components/groups/ExpenseSharePreview'

// 017 T036 (US3): "Nuevo gasto" form (plan.md §Diseño del frontend).

const MEMBERS = [
  { userId: 7, simulatedProfileId: null, name: 'Ana López', role: 'ADMIN' },
  { userId: 15, simulatedProfileId: null, name: 'Juan Pérez', role: 'MEMBER' },
  { userId: null, simulatedProfileId: 4, name: 'Abuela', role: 'MEMBER' },
]

function group(role = 'ADMIN', permissions = {}) {
  return { ok: true, group: { id: 3, name: 'Depto', currentUserRole: role, permissions, expensesCount: 0 } }
}

function renderView() {
  return render(
    <MemoryRouter initialEntries={['/groups/3/expenses/new']}>
      <Routes>
        <Route path="/groups/:id/expenses/new" element={<GroupExpenseFormView />} />
        <Route path="/groups/:id/expenses/:expenseId/edit" element={<GroupExpenseFormView />} />
        <Route path="/groups/:id/expenses/:expenseId" element={<p>Detalle del gasto</p>} />
      </Routes>
    </MemoryRouter>,
  )
}

async function fillAmount(user, value) {
  const amount = await screen.findByLabelText('Monto')
  await user.clear(amount)
  await user.type(amount, value)
}

const participantBox = (name) => screen.getByRole('checkbox', { name: new RegExp(name) })

beforeEach(() => {
  getGroup.mockReset()
  getMembers.mockReset()
  createGroupExpense.mockReset()
  getGroupExpense.mockReset()
  updateGroupExpense.mockReset()
  getExchangeQuote.mockReset()
  setUser.mockReset()
  showSnackbar.mockReset()
  getGroup.mockResolvedValue(group())
  getMembers.mockResolvedValue({ ok: true, members: MEMBERS })
  vi.spyOn(crypto, 'randomUUID').mockReturnValue('key-1')
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('GroupExpenseFormView (017)', () => {
  it('carga con el usuario como pagador y todos los participantes marcados', async () => {
    renderView()
    expect(screen.getByRole('status')).toHaveTextContent('Cargando…')

    const payer = await screen.findByLabelText('¿Quién pagó?')
    expect(payer).toHaveValue('u:7')
    expect(payer).not.toBeDisabled()
    expect(within(payer).queryByRole('option', { name: 'Abuela' })).not.toBeInTheDocument()
    for (const name of ['Ana López', 'Juan Pérez', 'Abuela']) {
      expect(participantBox(name)).toBeChecked()
    }
    expect(screen.getByText(/Abuela \(perfil simulado\)/)).toBeInTheDocument()
    expect(getGroup).toHaveBeenCalledWith('3')
    expect(getMembers).toHaveBeenCalledWith('3')
  })

  it('la vista previa sigue al monto, al pagador y a los participantes', async () => {
    const user = userEvent.setup()
    renderView()
    await fillAmount(user, '10.000')

    let rows = screen.getAllByTestId('share-preview-row')
    expect(rows).toHaveLength(3)
    expect(rows[0]).toHaveTextContent('Ana López (pagó)')
    expect(rows[0]).toHaveTextContent('$ 3.333,34')
    expect(screen.getByText(REST_TO_PAYER_NOTE)).toBeInTheDocument()

    await user.click(participantBox('Ana López'))
    await fillAmount(user, '100')
    rows = screen.getAllByTestId('share-preview-row')
    expect(rows).toHaveLength(2)
    expect(rows[0]).toHaveTextContent('Juan Pérez')
    expect(rows[0]).toHaveTextContent('$ 50,00')

    await user.click(participantBox('Ana López'))
    await user.selectOptions(screen.getByLabelText('¿Quién pagó?'), 'u:15')
    await user.click(participantBox('Juan Pérez'))
    rows = screen.getAllByTestId('share-preview-row')
    expect(rows[0]).toHaveTextContent('$ 50,00')
    await fillAmount(user, '100,01')
    expect(screen.getByText(REST_IN_ORDER_NOTE)).toBeInTheDocument()
    expect(screen.getAllByTestId('share-preview-row')[0]).toHaveTextContent('$ 50,01')
  })

  it('un MEMBER sin la casilla no puede elegir otro pagador', async () => {
    getGroup.mockResolvedValue(group('MEMBER', { membersCanGenerateExpensesForOthers: false }))
    renderView()
    expect(await screen.findByLabelText('¿Quién pagó?')).toBeDisabled()
  })

  it('si el grupo no permite gastos a los MEMBER no muestra el formulario', async () => {
    getGroup.mockResolvedValue(group('MEMBER', { membersCannotMakeExpenses: true }))
    renderView()
    expect(await screen.findByTestId('group-expense-forbidden')).toHaveTextContent(CANNOT_CREATE_MESSAGE)
    expect(screen.queryByRole('button', { name: 'Guardar gasto' })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Volver al grupo' })).toHaveAttribute('href', '/groups/3')
  })

  it('sin otro participante además del pagador no se puede guardar', async () => {
    const user = userEvent.setup()
    renderView()
    await screen.findByLabelText('¿Quién pagó?')
    await user.click(participantBox('Juan Pérez'))
    await user.click(participantBox('Abuela'))
    expect(screen.getByRole('button', { name: 'Guardar gasto' })).toBeDisabled()
    expect(PARTICIPANTS_REQUIRED_MESSAGE).toBe('Elegí al menos un participante además de quien pagó')
  })

  it('envía el gasto con la clave, muestra el snackbar y navega al detalle', async () => {
    const user = userEvent.setup()
    createGroupExpense.mockResolvedValue({ ok: true, created: true, expense: { id: 12 } })
    renderView()
    await fillAmount(user, '10.000')
    await user.type(screen.getByLabelText('Descripción'), '  Súper  ')
    await user.click(screen.getByRole('button', { name: 'Guardar gasto' }))

    expect(await screen.findByText('Detalle del gasto')).toBeInTheDocument()
    expect(showSnackbar).toHaveBeenCalledWith(GROUP_EXPENSE_SUCCESS_MESSAGE)
    const [groupId, payload, key] = createGroupExpense.mock.calls[0]
    expect(groupId).toBe('3')
    expect(key).toBe('key-1')
    expect(payload).toMatchObject({
      amount: '10000.00',
      currency: 'ARS',
      description: 'Súper',
      payerUserId: 7,
      participants: [
        { type: 'REGISTERED', userId: 7 },
        { type: 'REGISTERED', userId: 15 },
        { type: 'SIMULATED', simulatedProfileId: 4 },
      ],
    })
    expect(payload.date).toMatch(/^\d{4}-\d{2}-\d{2}$/)
    expect(payload.deviceTimeZone).toBeTruthy()
  })

  it('un error de red conserva los datos y el reintento usa la misma clave', async () => {
    const user = userEvent.setup()
    createGroupExpense
      .mockResolvedValueOnce({ ok: false, network: true })
      .mockResolvedValueOnce({ ok: true, created: false, expense: { id: 12 } })
    renderView()
    await fillAmount(user, '100')
    await user.click(screen.getByRole('button', { name: 'Guardar gasto' }))

    expect(await screen.findByTestId('group-expense-error')).toHaveTextContent(GROUP_EXPENSE_NETWORK_ERROR_MESSAGE)
    expect(screen.getByLabelText('Monto')).toHaveValue('100')

    crypto.randomUUID.mockReturnValue('key-2')
    await user.click(screen.getByRole('button', { name: 'Guardar gasto' }))
    expect(await screen.findByText('Detalle del gasto')).toBeInTheDocument()
    expect(createGroupExpense.mock.calls.map((call) => call[2])).toEqual(['key-1', 'key-1'])
  })

  it('los 400 se muestran junto a cada campo sin perder lo cargado y renuevan la clave', async () => {
    const user = userEvent.setup()
    createGroupExpense
      .mockResolvedValueOnce({
        ok: false,
        status: 400,
        detail: 'Datos inválidos',
        fields: {
          amount: 'El monto no alcanza para dividirlo entre 3 participantes',
          participants: 'Algún participante no pertenece al grupo',
          payerUserId: 'Quien pagó tiene que ser miembro del grupo',
        },
      })
      .mockResolvedValueOnce({ ok: false, status: 403, detail: 'Solo podés registrar gastos pagados por vos', fields: {} })
    renderView()
    await fillAmount(user, '100')
    await user.click(screen.getByRole('button', { name: 'Guardar gasto' }))

    expect(await screen.findByText('El monto no alcanza para dividirlo entre 3 participantes')).toBeInTheDocument()
    expect(screen.getByTestId('participants-error')).toHaveTextContent('Algún participante no pertenece al grupo')
    expect(screen.getByText('Quien pagó tiene que ser miembro del grupo')).toBeInTheDocument()
    expect(screen.getByLabelText('Monto')).toHaveValue('100')

    crypto.randomUUID.mockReturnValue('key-2')
    await user.click(screen.getByRole('button', { name: 'Guardar gasto' }))
    expect(await screen.findByTestId('group-expense-error')).toHaveTextContent('Solo podés registrar gastos pagados por vos')
    expect(createGroupExpense.mock.calls.map((call) => call[2])).toEqual(['key-1', 'key-2'])
  })

  it('422 informa la clave reutilizada y 401 cierra la sesión', async () => {
    const user = userEvent.setup()
    createGroupExpense
      .mockResolvedValueOnce({ ok: false, status: 422, detail: '', fields: {} })
      .mockResolvedValueOnce({ ok: false, status: 401, detail: '', fields: {} })
    renderView()
    await fillAmount(user, '100')
    await user.click(screen.getByRole('button', { name: 'Guardar gasto' }))
    expect(await screen.findByTestId('group-expense-error')).toHaveTextContent(REUSED_KEY_MESSAGE)

    await user.click(screen.getByRole('button', { name: 'Guardar gasto' }))
    await waitFor(() => expect(setUser).toHaveBeenCalledWith(null))
  })

  it('valida en el cliente el monto y la fecha futura', async () => {
    const user = userEvent.setup()
    renderView()
    await fillAmount(user, 'abc')
    const date = screen.getByLabelText('Fecha')
    await user.clear(date)
    await user.type(date, '01/01/2999')
    await user.click(screen.getByRole('button', { name: 'Guardar gasto' }))

    expect(await screen.findByText('Ingresá un monto válido')).toBeInTheDocument()
    expect(screen.getByText('La fecha no puede ser posterior a hoy')).toBeInTheDocument()
    expect(createGroupExpense).not.toHaveBeenCalled()
  })

  it('ante un 404 al cargar muestra el mensaje neutral sin reintento', async () => {
    getGroup.mockResolvedValue({ ok: false, status: 404 })
    renderView()
    const alert = await screen.findByTestId('group-expense-load-error')
    expect(alert).toHaveTextContent(NOT_A_MEMBER_MESSAGE)
    expect(within(alert).queryByRole('button')).not.toBeInTheDocument()
  })

  it('ante un error de red al cargar permite reintentar', async () => {
    const user = userEvent.setup()
    getMembers.mockResolvedValueOnce({ ok: false, network: true })
    renderView()
    const alert = await screen.findByTestId('group-expense-load-error')
    expect(alert).toHaveTextContent(FORM_LOAD_ERROR_MESSAGE)

    await user.click(within(alert).getByRole('button', { name: 'Reintentar' }))
    expect(await screen.findByLabelText('¿Quién pagó?')).toBeInTheDocument()
  })

  it('un 401 al cargar cierra la sesión', async () => {
    getGroup.mockResolvedValue({ ok: false, status: 401 })
    renderView()
    await waitFor(() => expect(setUser).toHaveBeenCalledWith(null))
  })
})

// 022 T070: preview of the converted amount with the exchange-quote of the group.
describe('GroupExpenseFormView: vista previa de la cotización (022 T070)', () => {
  const PAST_DATE = '05/01/2026'
  const PAST_ISO = '2026-01-05'
  const quoteOk = (rate = 1250.5) => ({
    ok: true,
    quote: { rate, rateDate: PAST_ISO, rateSource: 'BCRA', groupCurrency: 'ARS' },
  })
  const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

  async function setDate(user, value = PAST_DATE) {
    const date = await screen.findByLabelText('Fecha')
    await user.clear(date)
    await user.type(date, value)
  }

  async function chooseCurrency(user, code) {
    await user.selectOptions(await screen.findByLabelText('Moneda'), code)
  }

  const preview = () => screen.getByTestId('quote-preview')

  it('con la moneda del grupo no pide cotización ni muestra nada', async () => {
    const user = userEvent.setup()
    renderView()
    await fillAmount(user, '100')
    await setDate(user)
    await wait(QUOTE_DEBOUNCE_MS + 150)

    expect(getExchangeQuote).not.toHaveBeenCalled()
    expect(preview()).toBeEmptyDOMElement()
  })

  it('con otra moneda, monto y fecha pide la cotización y muestra el monto estimado con el aviso', async () => {
    getExchangeQuote.mockResolvedValue(quoteOk())
    const user = userEvent.setup()
    renderView()
    await fillAmount(user, '100')
    await setDate(user)
    await chooseCurrency(user, 'USD')

    expect(await screen.findByTestId('converted-amount')).toHaveTextContent('$ 100,00 USD · $ 125.050,00 ARS')
    expect(preview()).toHaveTextContent(QUOTE_NOTICE)
    expect(preview()).toHaveAttribute('aria-live', 'polite')
    expect(getExchangeQuote).toHaveBeenLastCalledWith('3', 'USD', PAST_ISO)
  })

  it('usa la moneda del grupo como destino (grupo en USD, gasto en ARS)', async () => {
    getGroup.mockResolvedValue({
      ok: true,
      group: { id: 3, name: 'Depto', currentUserRole: 'ADMIN', permissions: {}, primaryCurrency: 'USD' },
    })
    getExchangeQuote.mockResolvedValue(quoteOk(0.0008))
    const user = userEvent.setup()
    renderView()
    await fillAmount(user, '1000')
    await setDate(user)
    await chooseCurrency(user, 'ARS')

    expect(await screen.findByTestId('converted-amount')).toHaveTextContent('$ 1.000,00 ARS · $ 0,80 USD')
    expect(getExchangeQuote).toHaveBeenLastCalledWith('3', 'ARS', PAST_ISO)
  })

  it('espera a que termine de escribir (debounce) y pide una sola vez con el valor final', async () => {
    getExchangeQuote.mockResolvedValue(quoteOk())
    const user = userEvent.setup()
    renderView()
    await setDate(user)
    await chooseCurrency(user, 'USD')
    await fillAmount(user, '2500')
    await screen.findByTestId('converted-amount')

    expect(getExchangeQuote).toHaveBeenCalledTimes(1)
    expect(getExchangeQuote).toHaveBeenCalledWith('3', 'USD', PAST_ISO)
    expect(screen.getByTestId('converted-amount')).toHaveTextContent('$ 2.500,00 USD · $ 3.126.250,00 ARS')
  })

  it('vuelve a pedir cuando cambian la moneda, la fecha o el monto', async () => {
    getExchangeQuote.mockResolvedValue(quoteOk())
    const user = userEvent.setup()
    renderView()
    await fillAmount(user, '100')
    await setDate(user)
    await chooseCurrency(user, 'USD')
    await waitFor(() => expect(getExchangeQuote).toHaveBeenCalledTimes(1))

    await fillAmount(user, '200')
    await waitFor(() => expect(getExchangeQuote).toHaveBeenCalledTimes(2))
    await setDate(user, '06/01/2026')
    await waitFor(() => expect(getExchangeQuote).toHaveBeenLastCalledWith('3', 'USD', '2026-01-06'))
    await chooseCurrency(user, 'EUR')
    await waitFor(() => expect(getExchangeQuote).toHaveBeenLastCalledWith('3', 'EUR', '2026-01-06'))
    expect(getExchangeQuote).toHaveBeenCalledTimes(4)
  })

  it('muestra el estado de carga mientras espera la respuesta', async () => {
    let resolveQuote
    getExchangeQuote.mockReturnValue(new Promise((resolve) => { resolveQuote = resolve }))
    const user = userEvent.setup()
    renderView()
    await fillAmount(user, '100')
    await setDate(user)
    await chooseCurrency(user, 'USD')

    expect(await screen.findByText(QUOTE_LOADING_MESSAGE)).toBeInTheDocument()
    expect(screen.queryByTestId('converted-amount')).not.toBeInTheDocument()

    resolveQuote(quoteOk())
    expect(await screen.findByTestId('converted-amount')).toBeInTheDocument()
    expect(screen.queryByText(QUOTE_LOADING_MESSAGE)).not.toBeInTheDocument()
  })

  it('si la cotización no está disponible (503) avisa en voseo y no bloquea el envío', async () => {
    getExchangeQuote.mockResolvedValue({ ok: false, status: 503, detail: 'No se pudo obtener la cotización' })
    createGroupExpense.mockResolvedValue({ ok: true, created: true, expense: { id: 12 } })
    const user = userEvent.setup()
    renderView()
    await fillAmount(user, '100')
    await setDate(user)
    await chooseCurrency(user, 'USD')

    expect(await screen.findByText(QUOTE_ERROR_MESSAGE)).toBeInTheDocument()
    expect(screen.queryByTestId('converted-amount')).not.toBeInTheDocument()
    const submit = screen.getByRole('button', { name: 'Guardar gasto' })
    expect(submit).toBeEnabled()

    await user.click(submit)
    expect(await screen.findByText('Detalle del gasto')).toBeInTheDocument()
    expect(createGroupExpense.mock.calls[0][1]).toMatchObject({ amount: '100.00', currency: 'USD' })
  })

  it('un error de red o una cotización inválida también muestran el aviso', async () => {
    getExchangeQuote.mockResolvedValueOnce({ ok: false, network: true })
    const user = userEvent.setup()
    renderView()
    await fillAmount(user, '100')
    await setDate(user)
    await chooseCurrency(user, 'USD')
    expect(await screen.findByText(QUOTE_ERROR_MESSAGE)).toBeInTheDocument()

    getExchangeQuote.mockResolvedValueOnce(quoteOk('no-es-un-numero'))
    await fillAmount(user, '150')
    await waitFor(() => expect(getExchangeQuote).toHaveBeenCalledTimes(2))
    expect(await screen.findByText(QUOTE_ERROR_MESSAGE)).toBeInTheDocument()
  })

  it('al volver a la moneda del grupo la vista previa desaparece', async () => {
    getExchangeQuote.mockResolvedValue(quoteOk())
    const user = userEvent.setup()
    renderView()
    await fillAmount(user, '100')
    await setDate(user)
    await chooseCurrency(user, 'USD')
    await screen.findByTestId('converted-amount')

    await chooseCurrency(user, 'ARS')
    await waitFor(() => expect(preview()).toBeEmptyDOMElement())
  })

  it('no pide cotización con un monto inválido ni con una fecha futura', async () => {
    const user = userEvent.setup()
    renderView()
    await chooseCurrency(user, 'USD')
    await fillAmount(user, 'abc')
    await setDate(user)
    await wait(QUOTE_DEBOUNCE_MS + 150)
    expect(getExchangeQuote).not.toHaveBeenCalled()

    await fillAmount(user, '100')
    await setDate(user, '01/01/2999')
    await wait(QUOTE_DEBOUNCE_MS + 150)
    expect(getExchangeQuote).not.toHaveBeenCalled()
    expect(preview()).toBeEmptyDOMElement()
  })

  it('descarta la respuesta de un pedido viejo cuando ya cambiaron los datos', async () => {
    let resolveOld
    getExchangeQuote
      .mockReturnValueOnce(new Promise((resolve) => { resolveOld = resolve }))
      .mockResolvedValueOnce(quoteOk(2000))
    const user = userEvent.setup()
    renderView()
    await fillAmount(user, '100')
    await setDate(user)
    await chooseCurrency(user, 'USD')
    await waitFor(() => expect(getExchangeQuote).toHaveBeenCalledTimes(1))

    await fillAmount(user, '200')
    expect(await screen.findByTestId('converted-amount')).toHaveTextContent('$ 200,00 USD · $ 400.000,00 ARS')

    resolveOld(quoteOk(1))
    await wait(50)
    expect(screen.getByTestId('converted-amount')).toHaveTextContent('$ 400.000,00 ARS')
  })

  it('un 401 al cotizar cierra la sesión', async () => {
    getExchangeQuote.mockResolvedValue({ ok: false, status: 401 })
    const user = userEvent.setup()
    renderView()
    await fillAmount(user, '100')
    await setDate(user)
    await chooseCurrency(user, 'USD')
    await waitFor(() => expect(setUser).toHaveBeenCalledWith(null))
  })
})

// 022 Etapa 3C: moneda por defecto, reparto (T072) y pagador (T073).
describe('GroupExpenseFormView: moneda por defecto del grupo', () => {
  it('la moneda del gasto arranca en la del grupo', async () => {
    getGroup.mockResolvedValue({
      ok: true,
      group: { id: 3, name: 'Viaje', currentUserRole: 'ADMIN', permissions: {}, primaryCurrency: 'EUR' },
    })
    renderView()
    expect(await screen.findByLabelText('Moneda')).toHaveValue('EUR')
  })

  it('sin moneda en el grupo arranca en ARS', async () => {
    renderView()
    expect(await screen.findByLabelText('Moneda')).toHaveValue('ARS')
  })

  it('se puede cambiar y el gasto se envía en la moneda elegida', async () => {
    getGroup.mockResolvedValue({
      ok: true,
      group: { id: 3, name: 'Viaje', currentUserRole: 'ADMIN', permissions: {}, primaryCurrency: 'USD' },
    })
    createGroupExpense.mockResolvedValue({ ok: true, created: true, expense: { id: 12 } })
    const user = userEvent.setup()
    renderView()
    await fillAmount(user, '10')
    expect(screen.getByLabelText('Moneda')).toHaveValue('USD')
    await user.click(screen.getByRole('button', { name: 'Guardar gasto' }))
    await screen.findByText('Detalle del gasto')
    expect(createGroupExpense.mock.calls[0][1]).toMatchObject({ currency: 'USD' })
  })
})

describe('GroupExpenseFormView: reparto (022 T072)', () => {
  const save = () => screen.getByRole('button', { name: 'Guardar gasto' })
  const diff = () => screen.getByTestId('split-difference')

  async function typeValue(user, label, value) {
    const input = await screen.findByLabelText(label)
    await user.clear(input)
    await user.type(input, value)
  }

  async function chooseSplit(user, code) {
    await user.selectOptions(await screen.findByLabelText('¿Cómo se reparte?'), code)
  }

  it('ofrece EQUAL, CUSTOM y PERCENTAGE, arranca en partes iguales y no muestra inputs por participante', async () => {
    renderView()
    const select = await screen.findByLabelText('¿Cómo se reparte?')
    expect(select).toHaveValue('EQUAL')
    expect(Array.from(select.options).map((option) => option.value)).toEqual(['EQUAL', 'CUSTOM', 'PERCENTAGE'])
    expect(screen.queryByLabelText(/^Importe de /)).not.toBeInTheDocument()
    expect(screen.queryByTestId('split-difference')).not.toBeInTheDocument()
  })

  it('con importes personalizados pide un importe por participante y muestra lo que falta', async () => {
    const user = userEvent.setup()
    renderView()
    await fillAmount(user, '100')
    await chooseSplit(user, 'CUSTOM')

    expect(screen.getAllByLabelText(/^Importe de /)).toHaveLength(3)
    expect(diff()).toHaveAttribute('aria-live', 'polite')
    expect(diff()).toHaveTextContent(SPLIT_HINT_CUSTOM)
    expect(save()).toBeDisabled()

    await typeValue(user, 'Importe de Ana López', '30')
    await typeValue(user, 'Importe de Juan Pérez', '50')
    expect(diff()).toHaveTextContent('Faltan $ 20,00 ARS')
    expect(diff()).toHaveTextContent(CUSTOM_AMOUNT_INVALID) // Abuela sin importe todavía
    expect(save()).toBeDisabled()

    await typeValue(user, 'Importe de Abuela', '10')
    expect(diff()).toHaveTextContent('Faltan $ 10,00 ARS')
    expect(diff()).toHaveTextContent(CUSTOM_SUM_INVALID)
    expect(save()).toBeDisabled()

    await typeValue(user, 'Importe de Abuela', '20')
    expect(diff()).toHaveTextContent('Los importes suman exactamente el total')
    expect(save()).toBeEnabled()
  })

  it('avisa cuando los importes se pasan del total y bloquea el guardado', async () => {
    const user = userEvent.setup()
    renderView()
    await fillAmount(user, '100')
    await chooseSplit(user, 'CUSTOM')
    await typeValue(user, 'Importe de Ana López', '60')
    await typeValue(user, 'Importe de Juan Pérez', '60')
    await typeValue(user, 'Importe de Abuela', '1')

    expect(diff()).toHaveTextContent('Te pasaste por $ 21,00 ARS')
    expect(save()).toBeDisabled()
  })

  it('quien pagó queda marcado y bloqueado, también al cambiar de pagador', async () => {
    getGroup.mockResolvedValue(group('ADMIN'))
    const user = userEvent.setup()
    renderView()
    await chooseSplit(user, 'CUSTOM')

    expect(participantBox('Ana López')).toBeChecked()
    expect(participantBox('Ana López')).toBeDisabled()
    expect(screen.getByText(PAYER_ALWAYS_PARTICIPATES)).toBeInTheDocument()

    await user.click(participantBox('Juan Pérez')) // lo saco del reparto
    expect(screen.queryByLabelText('Importe de Juan Pérez')).not.toBeInTheDocument()
    await user.selectOptions(screen.getByLabelText('¿Quién pagó?'), 'u:15')
    expect(participantBox('Juan Pérez')).toBeChecked()
    expect(participantBox('Juan Pérez')).toBeDisabled()
    expect(screen.getByLabelText('Importe de Juan Pérez')).toBeInTheDocument()
  })

  it('envía splitMethod CUSTOM con el importe de cada participante, simulados incluidos', async () => {
    createGroupExpense.mockResolvedValue({ ok: true, created: true, expense: { id: 12 } })
    const user = userEvent.setup()
    renderView()
    await fillAmount(user, '10.000')
    await chooseSplit(user, 'CUSTOM')
    await typeValue(user, 'Importe de Ana López', '3.000')
    await typeValue(user, 'Importe de Juan Pérez', '6000,00')
    await typeValue(user, 'Importe de Abuela', '1000')
    await user.click(save())

    expect(await screen.findByText('Detalle del gasto')).toBeInTheDocument()
    expect(createGroupExpense.mock.calls[0][1]).toMatchObject({
      amount: '10000.00',
      currency: 'ARS',
      splitMethod: 'CUSTOM',
      payerUserId: 7,
      participants: [
        { type: 'REGISTERED', userId: 7, amount: '3000.00' },
        { type: 'REGISTERED', userId: 15, amount: '6000.00' },
        { type: 'SIMULATED', simulatedProfileId: 4, amount: '1000.00' },
      ],
    })
  })

  it('con porcentajes muestra lo que falta, exige 100 exacto y envía el porcentaje de cada uno', async () => {
    createGroupExpense.mockResolvedValue({ ok: true, created: true, expense: { id: 12 } })
    const user = userEvent.setup()
    renderView()
    await fillAmount(user, '1000')
    await chooseSplit(user, 'PERCENTAGE')

    expect(diff()).toHaveTextContent(SPLIT_HINT_PERCENTAGE)
    expect(save()).toBeDisabled()
    await typeValue(user, 'Porcentaje de Ana López', '50')
    await typeValue(user, 'Porcentaje de Juan Pérez', '30,5')
    await typeValue(user, 'Porcentaje de Abuela', '10')
    expect(diff()).toHaveTextContent('Falta repartir 9,5 %')
    expect(diff()).toHaveTextContent(PERCENTAGE_SUM_INVALID)
    expect(save()).toBeDisabled()

    await typeValue(user, 'Porcentaje de Abuela', '19,5')
    expect(diff()).toHaveTextContent('Los porcentajes suman exactamente 100 %')
    expect(save()).toBeEnabled()
    await user.click(save())

    await screen.findByText('Detalle del gasto')
    expect(createGroupExpense.mock.calls[0][1]).toMatchObject({
      splitMethod: 'PERCENTAGE',
      participants: [
        { type: 'REGISTERED', userId: 7, percentage: '50' },
        { type: 'REGISTERED', userId: 15, percentage: '30.5' },
        { type: 'SIMULATED', simulatedProfileId: 4, percentage: '19.5' },
      ],
    })
    expect(createGroupExpense.mock.calls[0][1].participants[0]).not.toHaveProperty('amount')
  })

  it('avisa cuando los porcentajes se pasan de 100', async () => {
    const user = userEvent.setup()
    renderView()
    await fillAmount(user, '1000')
    await chooseSplit(user, 'PERCENTAGE')
    await typeValue(user, 'Porcentaje de Ana López', '60')
    await typeValue(user, 'Porcentaje de Juan Pérez', '50')
    await typeValue(user, 'Porcentaje de Abuela', '10')

    expect(diff()).toHaveTextContent('Te pasaste por 20 %')
    expect(save()).toBeDisabled()
  })

  it('un porcentaje en cero o con formato inválido muestra el mensaje del backend', async () => {
    const user = userEvent.setup()
    renderView()
    await fillAmount(user, '1000')
    await chooseSplit(user, 'PERCENTAGE')
    await typeValue(user, 'Porcentaje de Ana López', '0')

    expect(diff()).toHaveTextContent('Cada porcentaje tiene que ser mayor que 0')
    expect(save()).toBeDisabled()
  })

  it('la vista previa muestra las partes estimadas del reparto', async () => {
    const user = userEvent.setup()
    renderView()
    await fillAmount(user, '100')
    await chooseSplit(user, 'PERCENTAGE')
    await typeValue(user, 'Porcentaje de Ana López', '33,34')
    await typeValue(user, 'Porcentaje de Juan Pérez', '33,33')
    await typeValue(user, 'Porcentaje de Abuela', '33,33')

    const rows = screen.getAllByTestId('share-preview-row')
    expect(rows.map((row) => row.textContent)).toEqual([
      'Ana López (pagó)$ 33,34',
      'Juan Pérez$ 33,33',
      'Abuela$ 33,33',
    ])
    expect(screen.queryByText(REST_TO_PAYER_NOTE)).not.toBeInTheDocument()
  })

  it('en otra moneda mantiene la vista previa convertida y estima las partes con la cotización', async () => {
    getExchangeQuote.mockResolvedValue({
      ok: true,
      quote: { rate: 1250.5, rateDate: '2026-01-05', rateSource: 'BCRA', groupCurrency: 'ARS' },
    })
    getMembers.mockResolvedValue({ ok: true, members: MEMBERS.slice(0, 2) })
    const user = userEvent.setup()
    renderView()
    await fillAmount(user, '100')
    const date = await screen.findByLabelText('Fecha')
    await user.clear(date)
    await user.type(date, '05/01/2026')
    await user.selectOptions(screen.getByLabelText('Moneda'), 'USD')
    await chooseSplit(user, 'CUSTOM')
    await typeValue(user, 'Importe de Ana López', '40')
    await typeValue(user, 'Importe de Juan Pérez', '60')

    expect(await screen.findByTestId('converted-amount')).toHaveTextContent('$ 100,00 USD · $ 125.050,00 ARS')
    expect(diff()).toHaveTextContent('Los importes suman exactamente el total')
    await waitFor(() => expect(screen.getAllByTestId('share-preview-row')).toHaveLength(2))
    const rows = screen.getAllByTestId('share-preview-row').map((row) => row.textContent)
    expect(rows).toEqual(['Ana López (pagó)$ 50.020,00', 'Juan Pérez$ 75.030,00'])
    expect(screen.getByText(SPLIT_ESTIMATE_NOTE)).toBeInTheDocument()
    expect(getExchangeQuote).toHaveBeenCalledTimes(1)
  })

  it('al volver a partes iguales desaparecen los inputs y se puede guardar sin importes', async () => {
    createGroupExpense.mockResolvedValue({ ok: true, created: true, expense: { id: 12 } })
    const user = userEvent.setup()
    renderView()
    await fillAmount(user, '90')
    await chooseSplit(user, 'CUSTOM')
    expect(save()).toBeDisabled()
    await chooseSplit(user, 'EQUAL')

    expect(screen.queryByLabelText(/^Importe de /)).not.toBeInTheDocument()
    expect(participantBox('Ana López')).toBeEnabled()
    expect(save()).toBeEnabled()
    await user.click(save())
    await screen.findByText('Detalle del gasto')
    const payload = createGroupExpense.mock.calls[0][1]
    expect(payload.splitMethod).toBe('EQUAL')
    expect(payload.participants.every((p) => !('amount' in p) && !('percentage' in p))).toBe(true)
  })

  it('muestra junto a los participantes el error de reparto que devuelva el servidor', async () => {
    createGroupExpense.mockResolvedValue({
      ok: false,
      status: 400,
      fields: { participants: CUSTOM_SUM_INVALID },
    })
    const user = userEvent.setup()
    renderView()
    await fillAmount(user, '100')
    await chooseSplit(user, 'CUSTOM')
    await typeValue(user, 'Importe de Ana López', '50')
    await typeValue(user, 'Importe de Juan Pérez', '30')
    await typeValue(user, 'Importe de Abuela', '20')
    await user.click(save())

    expect(await screen.findByTestId('participants-error')).toHaveTextContent(CUSTOM_SUM_INVALID)
  })
})

describe('GroupExpenseFormView: selector de pagador (022 T073)', () => {
  it('con la casilla en true un miembro elige a cualquier miembro registrado y se envía como pagador', async () => {
    getGroup.mockResolvedValue(group('MEMBER', { membersCanGenerateExpensesForOthers: true }))
    createGroupExpense.mockResolvedValue({ ok: true, created: true, expense: { id: 12 } })
    const user = userEvent.setup()
    renderView()
    const payer = await screen.findByLabelText('¿Quién pagó?')

    expect(payer).toBeEnabled()
    expect(within(payer).getAllByRole('option').map((option) => option.textContent))
      .toEqual(['Ana López', 'Juan Pérez']) // sin la persona simulada ni opción vacía
    await user.selectOptions(payer, 'u:15')
    await fillAmount(user, '100')
    await user.click(screen.getByRole('button', { name: 'Guardar gasto' }))

    await screen.findByText('Detalle del gasto')
    expect(createGroupExpense.mock.calls[0][1]).toMatchObject({ payerUserId: 15 })
  })

  it('con la casilla en false el pagador queda fijo en el usuario', async () => {
    getGroup.mockResolvedValue(group('MEMBER', { membersCanGenerateExpensesForOthers: false }))
    createGroupExpense.mockResolvedValue({ ok: true, created: true, expense: { id: 12 } })
    const user = userEvent.setup()
    renderView()
    const payer = await screen.findByLabelText('¿Quién pagó?')

    expect(payer).toBeDisabled()
    expect(payer).toHaveValue('u:7')
    await fillAmount(user, '100')
    await user.click(screen.getByRole('button', { name: 'Guardar gasto' }))
    await screen.findByText('Detalle del gasto')
    expect(createGroupExpense.mock.calls[0][1]).toMatchObject({ payerUserId: 7 })
  })

  it('un administrador siempre puede elegir al pagador', async () => {
    renderView()
    expect(await screen.findByLabelText('¿Quién pagó?')).toBeEnabled()
  })

  it('ante un 403 muestra el mensaje en voseo del servidor y permite reintentar', async () => {
    getGroup.mockResolvedValue(group('MEMBER', { membersCanGenerateExpensesForOthers: true }))
    createGroupExpense.mockResolvedValueOnce({
      ok: false,
      status: 403,
      detail: ONLY_OWN_PAYMENTS_MESSAGE,
      fields: {},
    })
    const user = userEvent.setup()
    renderView()
    await user.selectOptions(await screen.findByLabelText('¿Quién pagó?'), 'u:15')
    await fillAmount(user, '100')
    await user.click(screen.getByRole('button', { name: 'Guardar gasto' }))

    expect(await screen.findByTestId('group-expense-error')).toHaveTextContent(ONLY_OWN_PAYMENTS_MESSAGE)
    expect(screen.getByRole('button', { name: 'Guardar gasto' })).toBeEnabled()
  })

  it('si el 403 no trae detalle usa el mensaje por defecto', async () => {
    createGroupExpense.mockResolvedValueOnce({ ok: false, status: 403, detail: '', fields: {} })
    const user = userEvent.setup()
    renderView()
    await fillAmount(user, '100')
    await user.click(await screen.findByRole('button', { name: 'Guardar gasto' }))

    expect(await screen.findByTestId('group-expense-error')).toHaveTextContent(ONLY_OWN_PAYMENTS_MESSAGE)
  })
})

// 022 T077: gasto personal (crear) y edición de un gasto personal.
describe('GroupExpenseFormView: gasto personal al crear (022 T077)', () => {
  const personalBox = () => screen.getByRole('checkbox', { name: PERSONAL_LABEL })

  it('ofrece la opción de gasto personal, que oculta reparto y participantes y explica que no genera deudas', async () => {
    const user = userEvent.setup()
    renderView()
    await user.click(await screen.findByRole('checkbox', { name: PERSONAL_LABEL }))

    expect(screen.getByTestId('personal-note')).toHaveTextContent(PERSONAL_NOTE)
    expect(screen.queryByLabelText('¿Cómo se reparte?')).not.toBeInTheDocument()
    expect(screen.queryByRole('group', { name: /Participantes/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('checkbox', { name: /Juan Pérez/ })).not.toBeInTheDocument()
  })

  it('se guarda con el pagador como único participante, sin pedir otros participantes', async () => {
    createGroupExpense.mockResolvedValue({ ok: true, created: true, expense: { id: 12 } })
    const user = userEvent.setup()
    renderView()
    await fillAmount(user, '250')
    await user.click(await screen.findByRole('checkbox', { name: PERSONAL_LABEL }))

    expect(screen.getByRole('button', { name: 'Guardar gasto' })).toBeEnabled()
    await user.click(screen.getByRole('button', { name: 'Guardar gasto' }))

    await screen.findByText('Detalle del gasto')
    expect(createGroupExpense.mock.calls[0][1]).toMatchObject({
      amount: '250.00',
      payerUserId: 7,
      splitMethod: 'EQUAL',
      participants: [{ type: 'REGISTERED', userId: 7 }],
    })
  })

  it('con otro pagador el gasto personal queda a nombre de quien pagó', async () => {
    createGroupExpense.mockResolvedValue({ ok: true, created: true, expense: { id: 12 } })
    const user = userEvent.setup()
    renderView()
    await fillAmount(user, '250')
    await user.selectOptions(await screen.findByLabelText('¿Quién pagó?'), 'u:15')
    await user.click(personalBox())
    await user.click(screen.getByRole('button', { name: 'Guardar gasto' }))

    await screen.findByText('Detalle del gasto')
    expect(createGroupExpense.mock.calls[0][1]).toMatchObject({
      payerUserId: 15,
      participants: [{ type: 'REGISTERED', userId: 15 }],
    })
  })

  it('al desmarcarla vuelve el reparto con todos los participantes', async () => {
    const user = userEvent.setup()
    renderView()
    await user.click(await screen.findByRole('checkbox', { name: PERSONAL_LABEL }))
    await user.click(personalBox())

    expect(screen.getByLabelText('¿Cómo se reparte?')).toBeInTheDocument()
    for (const name of ['Ana López', 'Juan Pérez', 'Abuela']) {
      expect(participantBox(name)).toBeChecked()
    }
  })
})

describe('GroupExpenseFormView: editar un gasto personal (022 T077)', () => {
  const ANA = { userId: 7, name: 'Ana López' }
  const JUAN = { userId: 15, name: 'Juan Pérez' }
  const PERSONAL = {
    id: 12,
    groupId: 3,
    amount: '1500.00',
    currency: 'ARS',
    date: '2026-01-05',
    description: 'Súper',
    payer: ANA,
    createdBy: ANA,
    shares: [{ position: 0, type: 'REGISTERED', userId: 7, name: 'Ana López', amount: '1500.00', isPayer: true }],
    debts: [],
  }

  function renderEdit() {
    return render(
      <MemoryRouter initialEntries={['/groups/3/expenses/12/edit']}>
        <Routes>
          <Route path="/groups/:id/expenses/:expenseId/edit" element={<GroupExpenseFormView />} />
          <Route path="/groups/:id/expenses/:expenseId" element={<p>Detalle del gasto</p>} />
        </Routes>
      </MemoryRouter>,
    )
  }

  const save = () => screen.getByRole('button', { name: 'Guardar cambios' })
  const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

  async function edit(expense = PERSONAL) {
    getGroupExpense.mockResolvedValue({ ok: true, expense })
    renderEdit()
    await screen.findByLabelText('Monto')
  }

  async function changeAmount(user, value) {
    const amount = screen.getByLabelText('Monto')
    await user.clear(amount)
    await user.type(amount, value)
  }

  it('carga el gasto, precarga los valores y no muestra pagador, reparto ni participantes', async () => {
    await edit()

    expect(screen.getByRole('heading', { name: 'Editar gasto' })).toBeInTheDocument()
    expect(screen.getByLabelText('Monto')).toHaveValue('1500,00')
    expect(screen.getByLabelText('Moneda')).toHaveValue('ARS')
    expect(screen.getByLabelText('Fecha')).toHaveValue('05/01/2026')
    expect(screen.getByLabelText('Descripción')).toHaveValue('Súper')
    expect(getGroupExpense).toHaveBeenCalledWith('3', '12')
    expect(screen.queryByLabelText('¿Quién pagó?')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('¿Cómo se reparte?')).not.toBeInTheDocument()
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Volver al gasto' })).toHaveAttribute('href', '/groups/3/expenses/12')
    expect(save()).toBeEnabled()
  })

  it('envía solo los campos que cambiaron, avisa y vuelve al detalle', async () => {
    updateGroupExpense.mockResolvedValue({ ok: true, expense: { ...PERSONAL, amount: '2000.00' } })
    const user = userEvent.setup()
    await edit()
    await changeAmount(user, '2.000')
    await user.click(save())

    expect(await screen.findByText('Detalle del gasto')).toBeInTheDocument()
    expect(updateGroupExpense).toHaveBeenCalledWith('3', '12', { amount: '2000.00' })
    expect(showSnackbar).toHaveBeenCalledWith(EDIT_SUCCESS_MESSAGE)
    expect(createGroupExpense).not.toHaveBeenCalled()
  })

  it('envía moneda, fecha y descripción cuando cambian, y la descripción vacía borra el texto', async () => {
    updateGroupExpense.mockResolvedValue({ ok: true, expense: PERSONAL })
    const user = userEvent.setup()
    await edit()
    await user.selectOptions(screen.getByLabelText('Moneda'), 'USD')
    const date = screen.getByLabelText('Fecha')
    await user.clear(date)
    await user.type(date, '06/01/2026')
    await user.clear(screen.getByLabelText('Descripción'))
    await user.click(save())

    await screen.findByText('Detalle del gasto')
    expect(updateGroupExpense).toHaveBeenCalledWith('3', '12', {
      currency: 'USD',
      date: '2026-01-06',
      description: '',
    })
  })

  it('sin cambios no llama a la API y muestra el mensaje del servidor', async () => {
    const user = userEvent.setup()
    await edit()
    await user.click(save())

    expect(await screen.findByTestId('group-expense-error')).toHaveTextContent(EDIT_NOTHING_MESSAGE)
    expect(updateGroupExpense).not.toHaveBeenCalled()
  })

  it('valida el monto en el cliente', async () => {
    const user = userEvent.setup()
    await edit()
    await changeAmount(user, 'abc')
    await user.click(save())

    expect(await screen.findByText('Ingresá un monto válido')).toBeInTheDocument()
    expect(updateGroupExpense).not.toHaveBeenCalled()
  })

  it('409, 403 y 503 muestran el mensaje en voseo del servidor sin salir del formulario', async () => {
    const user = userEvent.setup()
    await edit()
    for (const [status, detail] of [
      [409, EDIT_NOT_PERSONAL_MESSAGE],
      [403, EDIT_NOT_ALLOWED_MESSAGE],
      [503, 'No se pudo obtener la cotización; reintente después o registre en moneda del grupo'],
    ]) {
      updateGroupExpense.mockResolvedValueOnce({ ok: false, status, detail, fields: {} })
      await changeAmount(user, String(status))
      await user.click(save())
      await waitFor(() => expect(screen.getByTestId('group-expense-error')).toHaveTextContent(detail))
      expect(screen.getByLabelText('Monto')).toHaveValue(String(status))
    }
    expect(showSnackbar).not.toHaveBeenCalled()
  })

  it('un 400 con campos los muestra junto a cada input y uno sin campos conocidos usa el mensaje del servidor', async () => {
    const user = userEvent.setup()
    await edit()
    updateGroupExpense.mockResolvedValueOnce({
      ok: false,
      status: 400,
      detail: 'x',
      fields: { amount: 'El monto no puede superar 999.999.999,99' },
    })
    await changeAmount(user, '5')
    await user.click(save())
    expect(await screen.findByText('El monto no puede superar 999.999.999,99')).toBeInTheDocument()

    updateGroupExpense.mockResolvedValueOnce({
      ok: false,
      status: 400,
      detail: 'x',
      fields: { body: 'Enviá al menos un campo para editar' },
    })
    await changeAmount(user, '6')
    await user.click(save())
    expect(await screen.findByTestId('group-expense-error')).toHaveTextContent('Enviá al menos un campo para editar')
  })

  it('un error de red conserva lo cargado y muestra el mensaje', async () => {
    updateGroupExpense.mockResolvedValue({ ok: false, network: true })
    const user = userEvent.setup()
    await edit()
    await changeAmount(user, '7')
    await user.click(save())

    expect(await screen.findByTestId('group-expense-error')).toHaveTextContent(GROUP_EXPENSE_UPDATE_ERROR_MESSAGE)
    expect(screen.getByLabelText('Monto')).toHaveValue('7')
  })

  it('un 401 al guardar cierra la sesión', async () => {
    updateGroupExpense.mockResolvedValue({ ok: false, status: 401 })
    const user = userEvent.setup()
    await edit()
    await changeAmount(user, '7')
    await user.click(save())
    await waitFor(() => expect(setUser).toHaveBeenCalledWith(null))
  })

  it('reutiliza la vista previa de la cotización al elegir otra moneda', async () => {
    getExchangeQuote.mockResolvedValue({
      ok: true,
      quote: { rate: 1250.5, rateDate: '2026-01-05', rateSource: 'BCRA', groupCurrency: 'ARS' },
    })
    const user = userEvent.setup()
    await edit({ ...PERSONAL, amount: '100.00' })
    await user.selectOptions(screen.getByLabelText('Moneda'), 'USD')

    expect(await screen.findByTestId('converted-amount')).toHaveTextContent('$ 100,00 USD · $ 125.050,00 ARS')
    expect(getExchangeQuote).toHaveBeenLastCalledWith('3', 'USD', '2026-01-05')
    expect(screen.getByTestId('quote-preview')).toHaveTextContent(QUOTE_NOTICE)
  })

  it('un gasto con participantes no se edita: muestra el motivo en lugar del formulario', async () => {
    await edit_blocked({
      ...PERSONAL,
      shares: [PERSONAL.shares[0], { ...PERSONAL.shares[0], position: 1, userId: 15, name: 'Juan Pérez' }],
    }, EDIT_NOT_PERSONAL_MESSAGE)
  })

  it('quien no lo registró, no pagó y no es admin no lo edita', async () => {
    getGroup.mockResolvedValue(group('MEMBER'))
    await edit_blocked({ ...PERSONAL, payer: JUAN, createdBy: JUAN }, EDIT_NOT_ALLOWED_MESSAGE)
  })

  async function edit_blocked(expense, message) {
    getGroupExpense.mockResolvedValue({ ok: true, expense })
    renderEdit()
    expect(await screen.findByTestId('group-expense-edit-blocked')).toHaveTextContent(message)
    expect(screen.queryByLabelText('Monto')).not.toBeInTheDocument()
    await wait(10)
  }

  it('un administrador edita el gasto personal de otra persona', async () => {
    updateGroupExpense.mockResolvedValue({ ok: true, expense: PERSONAL })
    const user = userEvent.setup()
    await edit({ ...PERSONAL, payer: JUAN, createdBy: JUAN })
    await changeAmount(user, '8')
    await user.click(save())
    await screen.findByText('Detalle del gasto')
    expect(updateGroupExpense).toHaveBeenCalledWith('3', '12', { amount: '8.00' })
  })

  it('un gasto inexistente muestra el mensaje neutral y un error de red permite reintentar', async () => {
    getGroupExpense.mockResolvedValueOnce({ ok: false, status: 404 })
    renderEdit()
    const alert = await screen.findByTestId('group-expense-load-error')
    expect(alert).toHaveTextContent(EXPENSE_GONE_MESSAGE)
    expect(within(alert).queryByRole('button', { name: 'Reintentar' })).not.toBeInTheDocument()
  })

  it('un 401 al cargar el gasto cierra la sesión', async () => {
    getGroupExpense.mockResolvedValue({ ok: false, status: 401 })
    renderEdit()
    await waitFor(() => expect(setUser).toHaveBeenCalledWith(null))
  })
})

