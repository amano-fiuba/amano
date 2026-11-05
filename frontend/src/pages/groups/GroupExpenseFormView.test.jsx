import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../../api/groupsClient', async (importOriginal) => ({
  ...await importOriginal(),
  getGroup: vi.fn(),
  getMembers: vi.fn(),
}))

vi.mock('../../api/groupExpensesClient', async (importOriginal) => ({
  ...await importOriginal(),
  createGroupExpense: vi.fn(),
}))

const setUser = vi.fn()
vi.mock('../../auth/AuthContext', () => ({
  useAuth: () => ({ user: { id: 7 }, setUser }),
}))

const showSnackbar = vi.fn()
vi.mock('../../components/Snackbar', () => ({
  useSnackbar: () => showSnackbar,
}))

import { getGroup, getMembers } from '../../api/groupsClient'
import {
  GROUP_EXPENSE_NETWORK_ERROR_MESSAGE,
  createGroupExpense,
} from '../../api/groupExpensesClient'
import GroupExpenseFormView, {
  CANNOT_CREATE_MESSAGE,
  FORM_LOAD_ERROR_MESSAGE,
  GROUP_EXPENSE_SUCCESS_MESSAGE,
  NOT_A_MEMBER_MESSAGE,
  PARTICIPANTS_REQUIRED_MESSAGE,
  REUSED_KEY_MESSAGE,
} from './GroupExpenseFormView'
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
