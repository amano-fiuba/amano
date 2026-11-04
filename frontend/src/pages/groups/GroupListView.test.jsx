import { render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router'

vi.mock('../../api/groupsClient', async (importOriginal) => ({
  ...await importOriginal(),
  listGroups: vi.fn(),
}))

import { listGroups } from '../../api/groupsClient'
import GroupListView from './GroupListView'

beforeEach(() => {
  listGroups.mockReset()
})

function renderView(entry = '/groups') {
  return render(
    <MemoryRouter initialEntries={[entry]}>
      <GroupListView />
    </MemoryRouter>,
  )
}

describe('GroupListView (T029/T031 read view)', () => {
  it('muestra la bienvenida de cuenta verificada cuando llega con state verifiedWelcome', async () => {
    listGroups.mockResolvedValue({ ok: true, groups: [] })

    render(
      <MemoryRouter
        initialEntries={[
          { pathname: '/groups', state: { verifiedWelcome: true } },
        ]}
      >
        <GroupListView />
      </MemoryRouter>,
    )

    expect(
      await screen.findByTestId('groups-verified-welcome'),
    ).toBeInTheDocument()
  })

  it('no muestra la bienvenida sin state de verificación', async () => {
    listGroups.mockResolvedValue({ ok: true, groups: [] })

    renderView()

    await screen.findByTestId('groups-empty')
    expect(
      screen.queryByTestId('groups-verified-welcome'),
    ).not.toBeInTheDocument()
  })

  it('renderiza los grupos con foto, nombre y rol del usuario', async () => {
    listGroups.mockResolvedValue({
      ok: true,
      groups: [
        { id: 3, name: 'Departamento 2026', photo: 'p3.jpg', currentUserRole: 'ADMIN' },
        { id: 5, name: 'Viaje a Mendoza', photo: null, currentUserRole: 'MEMBER' },
      ],
    })

    renderView()

    expect(await screen.findByText('Departamento 2026')).toBeInTheDocument()
    expect(screen.getByText('Viaje a Mendoza')).toBeInTheDocument()
    expect(document.querySelector('img')).toHaveAttribute('src', 'p3.jpg')
    expect(screen.getByTestId('group-avatar-default')).toBeInTheDocument()
    expect(screen.getByTestId('group-role-3')).toHaveTextContent(/Administrador/)
    expect(screen.getByTestId('group-role-5')).toHaveTextContent(/Miembro/)
  })

  it('muestra el estado vacío cuando el usuario no tiene grupos', async () => {
    listGroups.mockResolvedValue({ ok: true, groups: [] })

    renderView()

    expect(await screen.findByTestId('groups-empty')).toHaveTextContent(
      /Todavía no sos miembro de ningún grupo/,
    )
  })

  it('muestra el estado de carga y luego el error de red', async () => {
    listGroups.mockResolvedValue({ ok: false, network: true })

    renderView()
    expect(screen.getByText(/Cargando…/)).toBeInTheDocument()

    expect(await screen.findByTestId('groups-error')).toHaveTextContent(
      /No se pudo conectar con el servidor/,
    )
    expect(screen.getByTestId('groups-empty')).toBeInTheDocument()
  })

  it('el error 401 también se muestra como error de conexión con el texto del client', async () => {
    listGroups.mockResolvedValue({ ok: false, status: 401, fields: { member: 'sin sesión' } })

    renderView()

    await waitFor(() =>
      expect(screen.getByTestId('groups-error')).toHaveTextContent(
        /No se pudo conectar con el servidor/,
      ),
    )
  })

  it('mantiene el enlace para crear un nuevo grupo', async () => {
    listGroups.mockResolvedValue({ ok: true, groups: [] })

    renderView()

    expect(await screen.findByRole('link', { name: /Crear nuevo grupo/i })).toHaveAttribute(
      'href',
      '/groups/new',
    )
  })
})
