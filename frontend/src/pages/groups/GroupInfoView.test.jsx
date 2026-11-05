import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router'

vi.mock('../../api/groupsClient', async (importOriginal) => ({
  ...await importOriginal(),
  getGroup: vi.fn(),
  leaveGroup: vi.fn(),
  getMembers: vi.fn(),
  addMember: vi.fn(),
}))

import { addMember, getGroup, getMembers, leaveGroup } from '../../api/groupsClient'
import GroupInfoView from './GroupInfoView'
import { AuthProvider } from '../../auth/AuthContext'

beforeEach(() => {
  getGroup.mockReset()
  leaveGroup.mockReset()
  getMembers.mockReset()
  addMember.mockReset()
  getMembers.mockResolvedValue({ ok: true, members: [] })
})

const GROUP = {
  id: 3,
  name: 'Departamento 2026',
  description: 'Gastos compartidos del depto',
  memberCount: 4,
  currentUserRole: 'ADMIN',
}

function renderView() {
  return render(
    <MemoryRouter initialEntries={['/groups/3']}>
      <AuthProvider>
        <GroupInfoView />
      </AuthProvider>
    </MemoryRouter>,
  )
}

describe('GroupInfoView (US6 read view + US5 panel)', () => {
  it('muestra el nombre, la descripción y el rol del usuario', async () => {
    getGroup.mockResolvedValue({ ok: true, group: GROUP })

    renderView()

    expect(await screen.findByText('Departamento 2026')).toBeInTheDocument()
    expect(screen.getByText(/Gastos compartidos del depto/)).toBeInTheDocument()
    expect(screen.getByTestId('group-role')).toHaveTextContent(/Administrador/)
  })

  it('muestra el error de grupo inexistente con el motivo 404', async () => {
    getGroup.mockResolvedValue({
      ok: false,
      status: 404,
      detail: 'No existe el grupo solicitado o no sos miembro',
    })

    renderView()

    expect(await screen.findByTestId('group-error')).toHaveTextContent(
      /No existe el grupo solicitado o no sos miembro/,
    )
  })

  it('muestra el mensaje de red cuando no hay conexión', async () => {
    getGroup.mockResolvedValue({ ok: false, network: true })

    renderView()

    expect(await screen.findByTestId('group-error')).toHaveTextContent(
      /No se pudo conectar con el servidor/,
    )
  })

  it('incluye el panel para abandonar el grupo cuando el usuario no es último admin', async () => {
    getGroup.mockResolvedValue({ ok: true, group: GROUP })
    leaveGroup.mockResolvedValue({ ok: true, groupLeft: true })

    renderView()

    fireEvent.click(await screen.findByRole('button', { name: /Abandonar grupo/i }))
    fireEvent.click(screen.getByRole('button', { name: /Confirmar/i }))

    expect(await screen.findByRole('status')).toHaveTextContent(/Se abandonó el grupo/i)
  })

  it('sugiere asignar otro admin cuando el usuario es último admin (memberCount 1)', async () => {
    getGroup.mockResolvedValue({
      ok: true,
      group: { ...GROUP, memberCount: 1 },
    })

    renderView()

    fireEvent.click(await screen.findByRole('button', { name: /Abandonar grupo/i }))

    expect(
      await screen.findByText(
        /Sos el último administrador: asigná otro administrador antes de salir/i,
      ),
    ).toBeInTheDocument()
  })

  it('siendo admin muestra agregar miembro y la configuración del grupo', async () => {
    getGroup.mockResolvedValue({ ok: true, group: GROUP })

    renderView()

    fireEvent.click(await screen.findByRole('button', { name: /^Agregar miembro$/i }))
    fireEvent.click(screen.getByRole('button', { name: /Configuración del grupo/i }))

    expect(screen.getByTestId('admin-controls')).toBeInTheDocument()
    // fix 2026-11-04: no manual refresh button anymore
    expect(screen.queryByRole('button', { name: /Actualizar lista/i })).not.toBeInTheDocument()
  })

  it('tras agregar un miembro actualiza la lista y el contador sin recargar (fix 2026-11-04)', async () => {
    getGroup
      .mockResolvedValueOnce({ ok: true, group: GROUP })
      .mockResolvedValueOnce({ ok: true, group: { ...GROUP, memberCount: 5 } })
    getMembers
      .mockResolvedValueOnce({ ok: true, members: [] })
      .mockResolvedValueOnce({
        ok: true,
        members: [{ userId: null, simulatedProfileId: 9, name: 'Abuela', avatar: null, role: 'MEMBER' }],
      })
    addMember.mockResolvedValue({ ok: true, member: { id: 9 } })

    renderView()

    fireEvent.click(await screen.findByRole('button', { name: /^Agregar miembro$/i }))
    fireEvent.click(screen.getByRole('radio', { name: /Perfil simulado/i }))
    fireEvent.change(screen.getByLabelText('Nombre del perfil simulado'), { target: { value: 'Abuela' } })
    fireEvent.change(screen.getByLabelText('Correo del perfil simulado'), { target: { value: 'abuela@example.com' } })
    fireEvent.click(screen.getByRole('button', { name: /Agregar perfil simulado/i }))

    expect(await screen.findByText('Abuela')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByTestId('group-role')).toHaveTextContent('5 miembros'))
    expect(getMembers).toHaveBeenCalledTimes(2)
    expect(getGroup).toHaveBeenCalledTimes(2)
  })

  it('siendo miembro no ve los controles de administración del grupo', async () => {
    getGroup.mockResolvedValue({ ok: true, group: { ...GROUP, currentUserRole: 'MEMBER' } })

    renderView()

    await waitFor(() => expect(screen.getByTestId('members-list')).toBeInTheDocument())
    expect(screen.queryByRole('button', { name: /Configuración del grupo/i }))
      .not.toBeInTheDocument()
  })

  it('muestra la tarjeta "Gastos" con la cantidad y "Nuevo gasto" (017 FR-021)', async () => {
    getGroup.mockResolvedValue({ ok: true, group: { ...GROUP, expensesCount: 2, permissions: {} } })

    renderView()

    const card = await screen.findByRole('region', { name: 'Gastos del grupo' })
    expect(within(card).getByTestId('group-expenses-count')).toHaveTextContent('2 gastos registrados')
    expect(within(card).getByRole('link', { name: 'Nuevo gasto' })).toBeInTheDocument()
  })
})
