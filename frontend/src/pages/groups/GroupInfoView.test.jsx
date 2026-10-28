import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router'

vi.mock('../../api/groupsClient', async (importOriginal) => ({
  ...await importOriginal(),
  getGroup: vi.fn(),
  leaveGroup: vi.fn(),
}))

import { getGroup, leaveGroup } from '../../api/groupsClient'
import GroupInfoView from './GroupInfoView'
import { AuthProvider } from '../../auth/AuthContext'

beforeEach(() => {
  getGroup.mockReset()
  leaveGroup.mockReset()
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
    expect(
      screen.getByRole('button', { name: /Actualizar lista/i }),
    ).toBeInTheDocument()
  })

  it('siendo miembro no ve los controles de administración del grupo', async () => {
    getGroup.mockResolvedValue({ ok: true, group: { ...GROUP, currentUserRole: 'MEMBER' } })

    renderView()

    await waitFor(() => expect(screen.getByTestId('members-list')).toBeInTheDocument())
    expect(screen.queryByRole('button', { name: /Configuración del grupo/i }))
      .not.toBeInTheDocument()
  })
})
